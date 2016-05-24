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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gitiles.doc.html.HtmlBuilder;

import org.apache.commons.lang3.StringUtils;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Outputs outline from HeaderNodes in the AST. */
class TocFormatter {
  private final HtmlBuilder html;
  private final int maxLevel;

  private int countH1;
  private List<Heading> outline;
  private Map<Heading, String> ids;

  private int level;

  TocFormatter(HtmlBuilder html, int maxLevel) {
    this.html = html;
    this.maxLevel = maxLevel;
  }

  void setRoot(Node doc) {
    outline = new ArrayList<>();
    Multimap<String, TocEntry> entries = ArrayListMultimap.create(16, 4);
    scan(doc, entries, new ArrayDeque<Heading>());
    ids = generateIds(entries);
  }

  private boolean include(Heading h) {
    if (h.getLevel() == 1) {
      return countH1 > 1;
    }
    return h.getLevel() <= maxLevel;
  }

  String idFromHeader(Heading header) {
    return ids.get(header);
  }

  void format() {
    int startLevel = countH1 > 1 ? 1 : 2;
    level = startLevel;

    html.open("div")
        .attribute("class", "toc")
        .attribute("role", "navigation")
        .open("h2")
        .appendAndEscape("Contents")
        .close("h2")
        .open("div")
        .attribute("class", "toc-aux")
        .open("ul");
    for (Heading header : outline) {
      outline(header);
    }
    while (level >= startLevel) {
      html.close("ul");
      level--;
    }
    html.close("div").close("div");
  }

  private void outline(Heading h) {
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
        .open("a")
        .attribute("href", "#" + id)
        .appendAndEscape(MarkdownUtil.getInnerText(h))
        .close("a")
        .close("li");
  }

  private void scan(Node node, Multimap<String, TocEntry> entries, Deque<Heading> stack) {
    if (node instanceof Heading) {
      scan((Heading) node, entries, stack);
    } else {
      for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        scan(c, entries, stack);
      }
    }
  }

  private void scan(Heading header, Multimap<String, TocEntry> entries, Deque<Heading> stack) {
    if (header.getLevel() == 1) {
      countH1++;
    }
    while (!stack.isEmpty() && stack.getLast().getLevel() >= header.getLevel()) {
      stack.removeLast();
    }

    NamedAnchor node = findAnchor(header);
    if (node != null) {
      entries.put(node.getName(), new TocEntry(stack, header, false, node.getName()));
      stack.add(header);
      outline.add(header);
      return;
    }

    String title = MarkdownUtil.getInnerText(header);
    if (title != null) {
      String id = idFromTitle(title);
      entries.put(id, new TocEntry(stack, header, true, id));
      stack.add(header);
      outline.add(header);
    }
  }

  private static NamedAnchor findAnchor(Node node) {
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if (c instanceof NamedAnchor) {
        return (NamedAnchor) c;
      }
      NamedAnchor anchor = findAnchor(c);
      if (anchor != null) {
        return anchor;
      }
    }
    return null;
  }

  private Map<Heading, String> generateIds(Multimap<String, TocEntry> entries) {
    Multimap<String, TocEntry> tmp = ArrayListMultimap.create(entries.size(), 2);
    for (Collection<TocEntry> headers : entries.asMap().values()) {
      if (headers.size() == 1) {
        TocEntry entry = Iterables.getOnlyElement(headers);
        tmp.put(entry.id, entry);
        continue;
      }

      // Try to deduplicate by prefixing with text derived from parents.
      for (TocEntry entry : headers) {
        if (!entry.generated) {
          tmp.put(entry.id, entry);
          continue;
        }

        StringBuilder b = new StringBuilder();
        for (Heading p : entry.path) {
          if (p.getLevel() > 1 || countH1 > 1) {
            String text = MarkdownUtil.getInnerText(p);
            if (text != null) {
              b.append(idFromTitle(text)).append('-');
            }
          }
        }
        b.append(idFromTitle(MarkdownUtil.getInnerText(entry.header)));
        entry.id = b.toString();
        tmp.put(entry.id, entry);
      }
    }

    Map<Heading, String> ids = Maps.newHashMapWithExpectedSize(tmp.size());
    for (Collection<TocEntry> headers : tmp.asMap().values()) {
      if (headers.size() == 1) {
        TocEntry entry = Iterables.getOnlyElement(headers);
        ids.put(entry.header, entry.id);
      } else {
        int i = 1;
        for (TocEntry entry : headers) {
          ids.put(entry.header, entry.id + "-" + (i++));
        }
      }
    }
    return ids;
  }

  private static class TocEntry {
    final Heading[] path;
    final Heading header;
    final boolean generated;
    String id;

    TocEntry(Deque<Heading> stack, Heading header, boolean generated, String id) {
      this.path = stack.toArray(new Heading[stack.size()]);
      this.header = header;
      this.generated = generated;
      this.id = id;
    }
  }

  private static String idFromTitle(String title) {
    StringBuilder b = new StringBuilder(title.length());
    for (char c : StringUtils.stripAccents(title).toCharArray()) {
      if (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9')) {
        b.append(c);
      } else if (c == ' ') {
        if (b.length() > 0 && b.charAt(b.length() - 1) != '-' && b.charAt(b.length() - 1) != '_') {
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
}
