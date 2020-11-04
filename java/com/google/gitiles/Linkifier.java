// Copyright 2012 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Linkifier for blocks of text such as commit message descriptions. */
public class Linkifier {
  private static final Logger log = LoggerFactory.getLogger(Linkifier.class);

  private static final String COMMENTLINK = "commentlink";
  private static final Pattern HTTP_URL_PATTERN;
  private static final Pattern CHANGE_ID_PATTERN;

  static {
    // HTTP URL regex adapted from com.google.gwtexpui.safehtml.client.SafeHtml.
    String part = "(?:" + "[a-zA-Z0-9$_.+!*',%;:@=?#/~<>-]" + "|&(?!lt;|gt;)" + ")";
    String httpUrl = "https?://" + part + "{2,}" + "(?:[(]" + part + "*" + "[)])*" + part + "*";
    HTTP_URL_PATTERN = Pattern.compile(httpUrl);
    CHANGE_ID_PATTERN = Pattern.compile("(\\bI[0-9a-f]{8,40}\\b)");
  }

  private final GitilesUrls urls;
  private final List<CommentLinkInfo> commentLinks;
  private final Pattern allPattern;

  public Linkifier(GitilesUrls urls, Config config) {
    this.urls = checkNotNull(urls, "urls");

    List<CommentLinkInfo> list = new ArrayList<>();
    list.add(new CommentLinkInfo(HTTP_URL_PATTERN, "$0"));

    List<String> patterns = new ArrayList<>();
    patterns.add(HTTP_URL_PATTERN.pattern());
    patterns.add(CHANGE_ID_PATTERN.pattern());

    for (String subsection : config.getSubsections(COMMENTLINK)) {
      String match = config.getString(COMMENTLINK, subsection, "match");
      String link = config.getString(COMMENTLINK, subsection, "link");
      String html = config.getString(COMMENTLINK, subsection, "html");
      if (html != null) {
        log.warn(
            "Beware: html in commentlinks is unsupported in gitiles; "
                + "Did you copy it from a gerrit config?");
      }
      if (Strings.isNullOrEmpty(match)) {
        log.warn("invalid commentlink.{}.match", subsection);
        continue;
      }
      Pattern pattern;
      try {
        pattern = Pattern.compile(match);
      } catch (PatternSyntaxException ex) {
        String msg = "invalid commentlink." + subsection + ".match";
        if (log.isDebugEnabled()) {
          log.debug(msg, ex);
        } else {
          log.warn("{}: {}", msg, ex.getMessage());
        }
        continue;
      }
      if (Strings.isNullOrEmpty(link)) {
        log.warn("invalid commentlink.{}.link", subsection);
        continue;
      }
      list.add(new CommentLinkInfo(pattern, link));
      patterns.add(match);
    }
    this.commentLinks = Collections.unmodifiableList(list);
    allPattern = Pattern.compile(Joiner.on('|').join(patterns));
  }

  public List<Map<String, String>> linkify(HttpServletRequest req, String message) {

    List<CommentLinkInfo> operationalCommentLinks = new ArrayList<>(commentLinks);
    // Because we're relying on 'req' as a dynamic parameter, we need to construct
    // the CommentLinkInfo for ChangeIds on the fly.
    String baseGerritUrl = urls.getBaseGerritUrl(req);

    if (baseGerritUrl != null) {
      CommentLinkInfo changeIds = new CommentLinkInfo(CHANGE_ID_PATTERN, baseGerritUrl + "#/q/$0");
      operationalCommentLinks.add(changeIds);
    }

    List<Map<String, String>> parsed = Lists.newArrayList();
    parsed.add(ImmutableMap.of("text", message));

    for (int index = 0; index < parsed.size(); index++) {
      if (parsed.get(index).get("url") != null) {
        continue;
      }
      Matcher m = allPattern.matcher(parsed.get(index).get("text"));
      if (!m.find()) {
        continue;
      }

      for (CommentLinkInfo cli : operationalCommentLinks) {
        // No need to apply more rules if this is already a link.
        if (parsed.get(index).get("url") != null) {
          break;
        }
        String text = parsed.get(index).get("text");
        parsed.remove(index);
        parsed.addAll(index, cli.linkify(text));
      }
    }
    return parsed;
  }
}
