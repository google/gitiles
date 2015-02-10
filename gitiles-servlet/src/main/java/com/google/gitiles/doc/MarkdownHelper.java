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

import com.google.common.base.Strings;

import org.pegdown.ast.HeaderNode;
import org.pegdown.ast.Node;
import org.pegdown.ast.TextNode;

public class MarkdownHelper {
  /** Check if anchor URL is like {@code /top.md}. */
  public static boolean isAbsolutePathToMarkdown(String url) {
    return url.length() >= 5
        && url.charAt(0) == '/' && url.charAt(1) != '/'
        && url.endsWith(".md");
  }

  /** Combine child nodes as string; this must be escaped for HTML. */
  public static String getInnerText(Node node) {
    if (node == null || node.getChildren().isEmpty()) {
      return null;
    }

    StringBuilder b = new StringBuilder();
    appendTextFromChildren(b, node);
    return Strings.emptyToNull(b.toString().trim());
  }

  private static void appendTextFromChildren(StringBuilder b, Node node) {
    for (Node child : node.getChildren()) {
      if (child instanceof TextNode) {
        b.append(((TextNode) child).getText());
      } else {
        appendTextFromChildren(b, child);
      }
    }
  }

  static String getTitle(Node node) {
    if (node instanceof HeaderNode) {
      if (((HeaderNode) node).getLevel() == 1) {
        return getInnerText(node);
      }
      return null;
    }

    for (Node child : node.getChildren()) {
      String title = getTitle(child);
      if (title != null) {
        return title;
      }
    }
    return null;
  }

  private MarkdownHelper() {
  }
}
