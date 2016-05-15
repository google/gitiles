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

import com.google.gitiles.doc.html.HtmlBuilder;
import com.google.template.soy.shared.restricted.EscapingConventions.FilterImageDataUri;
import com.google.template.soy.shared.restricted.Sanitizers;

import org.commonmark.node.Heading;
import org.commonmark.node.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Navbar {
  private static final Pattern REF_LINK =
      Pattern.compile("^\\[(logo|home)\\]:\\s*(.+)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

  static Map<String, Object> bannerSoyData(
      ImageLoader img, MarkdownToHtml toHtml, String navMarkdown, Node nav) {
    Map<String, Object> data = new HashMap<>();
    data.put("siteTitle", null);
    data.put("logoUrl", null);
    data.put("homeUrl", null);

    if (nav == null) {
      return data;
    }

    for (Node c = nav.getFirstChild(); c != null; c = c.getNext()) {
      if (c instanceof Heading) {
        Heading h = (Heading) c;
        if (h.getLevel() == 1) {
          data.put("siteTitle", MarkdownUtil.getInnerText(h));
          h.unlink();
          break;
        }
      }
    }

    Matcher m = REF_LINK.matcher(navMarkdown);
    while (m.find()) {
      String key = m.group(1).toLowerCase();
      String url = m.group(2).trim();
      switch (key) {
        case "logo":
          data.put("logoUrl", toImgSrc(img, url));
          break;

        case "home":
          data.put("homeUrl", toHtml.href(url));
          break;
      }
    }

    return data;
  }

  private static Object toImgSrc(ImageLoader img, String url) {
    if (HtmlBuilder.isValidHttpUri(url)) {
      return url;
    }

    if (HtmlBuilder.isImageDataUri(url)) {
      return Sanitizers.filterImageDataUri(url);
    }

    if (img != null) {
      return Sanitizers.filterImageDataUri(img.loadImage(url));
    }

    return FilterImageDataUri.INSTANCE.getInnocuousOutput();
  }

  private Navbar() {}
}
