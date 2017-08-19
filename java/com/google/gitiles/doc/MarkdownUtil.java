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

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

class MarkdownUtil {
  /** Combine child nodes as string; this must be escaped for HTML. */
  static String getInnerText(Node node) {
    if (node == null || node.getFirstChild() == null) {
      return null;
    }

    StringBuilder b = new StringBuilder();
    appendTextFromChildren(b, node);
    return Strings.emptyToNull(b.toString().trim());
  }

  private static void appendTextFromChildren(StringBuilder b, Node node) {
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if (c instanceof Text) {
        b.append(((Text) c).getLiteral());
      } else {
        appendTextFromChildren(b, c);
      }
    }
  }

  static String getTitle(Node node) {
    if (node instanceof Heading) {
      if (((Heading) node).getLevel() == 1) {
        return getInnerText(node);
      }
      return null;
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      String title = getTitle(c);
      if (title != null) {
        return title;
      }
    }
    return null;
  }

  static void trimPreviousWhitespace(Node node) {
    Node prev = node.getPrevious();
    if (prev instanceof Text) {
      Text prevText = (Text) prev;
      String s = prevText.getLiteral();
      prevText.setLiteral(CharMatcher.whitespace().trimTrailingFrom(s));
    }
  }

  private MarkdownUtil() {}
}
