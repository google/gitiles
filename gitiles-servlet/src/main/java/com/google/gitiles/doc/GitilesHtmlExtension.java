// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles.doc;

import com.google.gitiles.doc.html.HtmlBuilder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.Extension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Node;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.commonmark.parser.Parser.ParserExtension;
import org.commonmark.parser.PostProcessor;

/**
 * Convert some {@link HtmlInline} and {@link HtmlBlock} to safe types.
 *
 * <p>Gitiles style Markdown accepts only a very small subset of HTML that is safe for use within
 * the document. This {@code PostProcessor} scans parsed nodes and converts them to safer types for
 * rendering:
 *
 * <ul>
 * <li>{@link HardLineBreak}
 * <li>{@link ThematicBreak}
 * <li>{@link NamedAnchor}
 * <li>{@link IframeBlock}
 * </ul>
 */
public class GitilesHtmlExtension implements ParserExtension {
  private static final Pattern BREAK = Pattern.compile("<(hr|br)\\s*/?>", Pattern.CASE_INSENSITIVE);

  private static final Pattern ANCHOR_OPEN =
      Pattern.compile("<a\\s+name=([\"'])([^\"'\\s]+)\\1>", Pattern.CASE_INSENSITIVE);
  private static final Pattern ANCHOR_CLOSE = Pattern.compile("</[aA]>");

  private static final Pattern IFRAME_OPEN =
      Pattern.compile("<iframe\\s+", Pattern.CASE_INSENSITIVE);
  private static final Pattern IFRAME_CLOSE =
      Pattern.compile("(?:/?>|</iframe>)", Pattern.CASE_INSENSITIVE);

  private static final Pattern ATTR =
      Pattern.compile(
          "\\s+([a-z-]+)\\s*=\\s*([^\\s\"'=<>`]+|'[^']*'|\"[^\"]*\")", Pattern.CASE_INSENSITIVE);

  public static Extension create() {
    return new GitilesHtmlExtension();
  }

  private GitilesHtmlExtension() {}

  @Override
  public void extend(Parser.Builder builder) {
    builder.postProcessor(new HtmlProcessor());
  }

  private static class HtmlProcessor implements PostProcessor {
    @Override
    public Node process(Node node) {
      node.accept(new HtmlVisitor());
      return node;
    }
  }

  private static class HtmlVisitor extends AbstractVisitor {
    @Override
    public void visit(HtmlInline node) {
      inline(node);
    }

    @Override
    public void visit(HtmlBlock node) {
      block(node);
    }
  }

  private static void inline(HtmlInline curr) {
    String html = curr.getLiteral();
    Matcher m = BREAK.matcher(html);
    if (m.matches()) {
      switch (m.group(1).toLowerCase()) {
        case "br":
          curr.insertAfter(new HardLineBreak());
          curr.unlink();
          return;

        case "hr":
          curr.insertAfter(new ThematicBreak());
          curr.unlink();
          return;
      }
    }

    m = ANCHOR_OPEN.matcher(html);
    if (m.matches()) {
      String name = m.group(2);
      Node next = curr.getNext();

      // HtmlInline{<a name="id">}HtmlInline{</a>}
      if (isAnchorClose(next)) {
        next.unlink();

        NamedAnchor anchor = new NamedAnchor();
        anchor.setName(name);
        curr.insertAfter(anchor);
        curr.unlink();
        MarkdownUtil.trimPreviousWhitespace(anchor);
        return;
      }
    }

    // Discard potentially unsafe HtmlInline.
    curr.unlink();
  }

  private static boolean isAnchorClose(Node n) {
    return n instanceof HtmlInline && ANCHOR_CLOSE.matcher(((HtmlInline) n).getLiteral()).matches();
  }

  private static void block(HtmlBlock curr) {
    String html = curr.getLiteral();
    Matcher m = IFRAME_OPEN.matcher(html);
    if (m.find()) {
      int start = m.end() - 1 /* leave whitespace */;
      m = IFRAME_CLOSE.matcher(html.substring(start));
      if (m.find()) {
        int end = start + m.start();
        IframeBlock f = iframe(html.substring(start, end));
        if (f != null) {
          curr.insertAfter(f);
          curr.unlink();
          return;
        }
      }
    }

    // Discard potentially unsafe HtmlBlock.
    curr.unlink();
  }

  private static IframeBlock iframe(String html) {
    IframeBlock iframe = new IframeBlock();
    Matcher m = ATTR.matcher(html);
    while (m.find()) {
      String att = m.group(1).toLowerCase();
      String val = attributeValue(m);
      switch (att) {
        case "src":
          if (!HtmlBuilder.isValidHttpUri(val)) {
            return null;
          }
          iframe.src = val;
          break;

        case "height":
          if (!HtmlBuilder.isValidCssDimension(val)) {
            return null;
          }
          iframe.height = val;
          break;

        case "width":
          if (!HtmlBuilder.isValidCssDimension(val)) {
            return null;
          }
          iframe.width = val;
          break;

        case "frameborder":
          iframe.border = !"0".equals(val);
          break;
      }
    }
    return iframe.src != null ? iframe : null;
  }

  private static String attributeValue(Matcher m) {
    String val = m.group(2);
    if (val.length() >= 2 && (val.charAt(0) == '\'' || val.charAt(0) == '"')) {
      // Capture group includes the opening and closing quotation marks if the
      // attribute value was quoted in the source document. Trim these.
      val = val.substring(1, val.length() - 1);
    }
    return val;
  }
}
