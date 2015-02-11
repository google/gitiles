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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.hash.Hashing;
import com.google.gitiles.doc.html.HtmlBuilder;

import org.apache.commons.lang3.StringUtils;
import org.pegdown.ast.HeaderNode;
import org.pegdown.ast.Node;
import org.pegdown.ast.RootNode;

import java.nio.charset.StandardCharsets;

/** Outputs outline from HeaderNodes in the AST. */
class TocFormatter {
  private final HtmlBuilder html;
  private final int maxLevel;

  private RootNode root;
  private Boolean hasToc;
  private int countH1;
  private BiMap<HeaderNode, String> ids;

  private int level;

  TocFormatter(HtmlBuilder html, int maxLevel) {
    this.html = html;
    this.maxLevel = maxLevel;
  }

  void setRoot(RootNode doc) {
    root = doc;
    hasToc = null;
    ids = HashBiMap.create();
  }

  boolean include(HeaderNode h) {
    init();
    if (!hasToc) {
      return false;
    } else if (h.getLevel() == 1) {
      return countH1 > 1;
    }
    return h.getLevel() <= maxLevel;
  }

  String idFromHeader(HeaderNode header) {
    String id = ids.get(header);
    if (id == null) {
      String title = MarkdownUtil.getInnerText(header);
      if (title == null) {
        return null;
      }

      id = idFromTitle(title);
      if (ids.values().contains(id)) {
        id = String.format("%s-%x",
            id,
            Hashing.md5().hashString(id, StandardCharsets.UTF_8).asInt());
      }
      ids.put(header, id);
    }
    return id;
  }

  void format() {
    init();

    int startLevel = countH1 > 1 ? 1 : 2;
    hasToc = true;
    level = startLevel;

    html.open("div")
        .attribute("class", "toc")
        .attribute("role", "navigation")
      .open("h2").appendAndEscape("Contents").close("h2")
      .open("div").attribute("class", "toc-aux")
      .open("ul");
    outline(root);
    while (level >= startLevel) {
      html.close("ul");
      level--;
    }
    html.close("div").close("div");
  }

  private void outline(Node node) {
    if (node instanceof HeaderNode) {
      outline((HeaderNode) node);
    } else {
      for (Node child : node.getChildren()) {
        outline(child);
      }
    }
  }

  private void outline(HeaderNode h) {
    if (!include(h)) {
      return;
    }

    String id = idFromHeader(h);
    if (id == null) {
      return;
    }

    while (level > h.getLevel()) {
      html.close("ul");
      level--;
    }
    while (level < h.getLevel()) {
      html.open("ul");
      level++;
    }

    html.open("li")
      .open("a").attribute("href", "#" + id)
      .appendAndEscape(MarkdownUtil.getInnerText(h))
      .close("a")
      .close("li");
  }

  private static String idFromTitle(String title) {
    StringBuilder b = new StringBuilder(title.length());
    for (char c : StringUtils.stripAccents(title).toCharArray()) {
      if (('a' <= c && c <= 'z')
          || ('A' <= c && c <= 'Z')
          || ('0' <= c && c <= '9')) {
        b.append(c);
      } else if (c == ' ') {
        if (b.length() > 0
            && b.charAt(b.length() - 1) != '-'
            && b.charAt(b.length() - 1) != '_') {
          b.append('-');
        }
      } else if (b.length() > 0
          && b.charAt(b.length() - 1) != '-'
          && b.charAt(b.length() - 1) != '_') {
        b.append('_');
      }
    }
    while (b.length() > 0) {
      char c = b.charAt(b.length() - 1);
      if (c == '-' || c == '_') {
        b.setLength(b.length() - 1);
        continue;
      }
      break;
    }
    return b.toString();
  }

  private void init() {
    if (hasToc == null) {
      hasToc = false;
      init(root);
    }
  }

  private void init(Node node) {
    if (node instanceof TocNode) {
      hasToc = true;
      return;
    } else if (node instanceof HeaderNode
        && ((HeaderNode) node).getLevel() == 1) {
      countH1++;
      return;
    }
    for (Node child : node.getChildren()) {
      init(child);
      if (hasToc && countH1 > 1) {
        break;
      }
    }
  }
}
