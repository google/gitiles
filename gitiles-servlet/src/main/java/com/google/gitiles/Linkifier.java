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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

/** Linkifier for blocks of text such as commit message descriptions. */
public class Linkifier {
  private static final Pattern LINK_PATTERN;

  static {
    // HTTP URL regex adapted from com.google.gwtexpui.safehtml.client.SafeHtml.
    String part = "(?:" +
        "[a-zA-Z0-9$_.+!*',%;:@=?#/~<>-]" +
        "|&(?!lt;|gt;)" +
        ")";
    String httpUrl = "https?://" +
        part + "{2,}" +
        "(?:[(]" + part + "*" + "[)])*" +
        part + "*";
    String changeId = "\\bI[0-9a-f]{8,40}\\b";
    LINK_PATTERN = Pattern.compile(Joiner.on("|").join(
        "(" + httpUrl + ")",
        "(" + changeId + ")"));
  }

  private final GitilesUrls urls;

  public Linkifier(GitilesUrls urls) {
    this.urls = checkNotNull(urls, "urls");
  }

  public List<Map<String, String>> linkify(HttpServletRequest req, String message) {
    String baseGerritUrl = urls.getBaseGerritUrl(req);
    List<Map<String, String>> parsed = Lists.newArrayList();
    Matcher m = LINK_PATTERN.matcher(message);
    int last = 0;
    while (m.find()) {
      addText(parsed, message.substring(last, m.start()));
      if (m.group(1) != null) {
        // Bare URL.
        parsed.add(link(m.group(1), m.group(1)));
      } else if (m.group(2) != null) {
        if (baseGerritUrl != null) {
          // Gerrit Change-Id.
          parsed.add(link(m.group(2), baseGerritUrl + "#/q/" + m.group(2) + ",n,z"));
        } else {
          addText(parsed, m.group(2));
        }
      }
      last = m.end();
    }
    addText(parsed, message.substring(last));
    return parsed;
  }

  private static Map<String, String> link(String text, String url) {
    return ImmutableMap.of("text", text, "url", url);
  }

  private static void addText(List<Map<String, String>> parts, String text) {
    if (text.isEmpty()) {
      return;
    }
    if (parts.isEmpty()) {
      parts.add(ImmutableMap.of("text", text));
    } else {
      Map<String, String> old = parts.get(parts.size() - 1);
      if (!old.containsKey("url")) {
        parts.set(parts.size() - 1, ImmutableMap.of("text", old.get("text") + text));
      } else {
        parts.add(ImmutableMap.of("text", text));
      }
    }
  }
}
