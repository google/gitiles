// Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.gitiles.doc;

import static java.util.stream.Collectors.toSet;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.gitiles.doc.html.HtmlBuilder;
import com.google.template.soy.shared.restricted.Sanitizers;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.eclipse.jgit.util.RawParseUtils;

class Navbar {
  private static final Pattern META_LINK =
      Pattern.compile(
          "^\\[(logo|home|extensions)\\]:\\s*(.+)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

  private MarkdownConfig cfg;
  private MarkdownToHtml fmt;
  private Node node;
  private String siteTitle;
  private String logoUrl;
  private String homeUrl;

  Navbar() {}

  MarkdownConfig getConfig() {
    return cfg;
  }

  Navbar setConfig(MarkdownConfig cfg) {
    this.cfg = cfg;
    return this;
  }

  Navbar setFormatter(MarkdownToHtml html) {
    this.fmt = html;
    return this;
  }

  Navbar setMarkdown(byte[] md) {
    if (md != null && md.length > 0) {
      parse(RawParseUtils.decode(md));
    }
    return this;
  }

  Map<String, Object> toSoyData() {
    Map<String, Object> data = new HashMap<>();
    data.put("siteTitle", siteTitle);
    data.put("logoUrl", logo());
    data.put("homeUrl", homeUrl != null ? fmt.href(homeUrl) : null);
    data.put("navbarHtml", node != null ? fmt.toSoyHtml(node) : null);
    return data;
  }

  private Object logo() {
    if (logoUrl == null) {
      return null;
    }

    String url = fmt.image(logoUrl);
    if (HtmlBuilder.isValidHttpUri(url)) {
      return url;
    } else if (HtmlBuilder.isImageDataUri(url)) {
      return Sanitizers.filterImageDataUri(url);
    } else {
      return SoyConstants.IMAGE_URI_INNOCUOUS_OUTPUT;
    }
  }

  private void parse(String markdown) {
    extractMetadata(markdown);
    node = GitilesMarkdown.parse(cfg, markdown);
    extractSiteTitle();
  }

  private void extractSiteTitle() {
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if (c instanceof Heading) {
        Heading h = (Heading) c;
        if (h.getLevel() == 1) {
          siteTitle = MarkdownUtil.getInnerText(h);
          h.unlink();
          break;
        }
      }
    }
  }

  private void extractMetadata(String markdown) {
    Matcher m = META_LINK.matcher(markdown);
    while (m.find()) {
      String key = m.group(1).toLowerCase();
      String url = m.group(2).trim();
      switch (key) {
        case "logo":
          logoUrl = url;
          break;
        case "home":
          homeUrl = url;
          break;
        case "extensions":
          Set<String> names = splitExtensionNames(url);
          cfg = cfg.copyWithExtensions(enabled(names), disabled(names));
          break;
      }
    }
  }

  private static Set<String> splitExtensionNames(String url) {
    return Splitter.on(CharMatcher.whitespace().or(CharMatcher.is(',')))
        .trimResults()
        .omitEmptyStrings()
        .splitToList(url)
        .stream()
        .map(String::toLowerCase)
        .collect(toSet());
  }

  private static Set<String> enabled(Set<String> names) {
    return names.stream().filter(n -> !n.startsWith("!")).collect(toSet());
  }

  private static Set<String> disabled(Set<String> names) {
    return names.stream().filter(n -> n.startsWith("!")).map(n -> n.substring(1)).collect(toSet());
  }
}
