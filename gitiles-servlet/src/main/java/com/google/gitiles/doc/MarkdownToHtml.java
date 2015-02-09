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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gitiles.doc.MarkdownUtil.getInnerText;

import com.google.common.base.Strings;
import com.google.gitiles.GitilesView;
import com.google.gitiles.ThreadSafePrettifyParser;
import com.google.gitiles.doc.html.HtmlBuilder;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.shared.restricted.EscapingConventions.FilterImageDataUri;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.StringUtils;
import org.pegdown.ast.AbbreviationNode;
import org.pegdown.ast.AutoLinkNode;
import org.pegdown.ast.BlockQuoteNode;
import org.pegdown.ast.BulletListNode;
import org.pegdown.ast.CodeNode;
import org.pegdown.ast.DefinitionListNode;
import org.pegdown.ast.DefinitionNode;
import org.pegdown.ast.DefinitionTermNode;
import org.pegdown.ast.ExpImageNode;
import org.pegdown.ast.ExpLinkNode;
import org.pegdown.ast.HeaderNode;
import org.pegdown.ast.HtmlBlockNode;
import org.pegdown.ast.InlineHtmlNode;
import org.pegdown.ast.ListItemNode;
import org.pegdown.ast.MailLinkNode;
import org.pegdown.ast.Node;
import org.pegdown.ast.OrderedListNode;
import org.pegdown.ast.ParaNode;
import org.pegdown.ast.QuotedNode;
import org.pegdown.ast.RefImageNode;
import org.pegdown.ast.RefLinkNode;
import org.pegdown.ast.ReferenceNode;
import org.pegdown.ast.RootNode;
import org.pegdown.ast.SimpleNode;
import org.pegdown.ast.SpecialTextNode;
import org.pegdown.ast.StrikeNode;
import org.pegdown.ast.StrongEmphSuperNode;
import org.pegdown.ast.SuperNode;
import org.pegdown.ast.TableBodyNode;
import org.pegdown.ast.TableCaptionNode;
import org.pegdown.ast.TableCellNode;
import org.pegdown.ast.TableColumnNode;
import org.pegdown.ast.TableHeaderNode;
import org.pegdown.ast.TableNode;
import org.pegdown.ast.TableRowNode;
import org.pegdown.ast.TextNode;
import org.pegdown.ast.VerbatimNode;
import org.pegdown.ast.WikiLinkNode;

import java.util.List;

import prettify.parser.Prettify;
import syntaxhighlight.ParseResult;

/**
 * Formats parsed markdown AST into HTML.
 * <p>
 * Callers must create a new instance for each RootNode.
 */
public class MarkdownToHtml implements Visitor {
  private final ReferenceMap references = new ReferenceMap();
  private final HtmlBuilder html = new HtmlBuilder();
  private final TocFormatter toc = new TocFormatter(html, 3);
  private final GitilesView view;
  private final Config cfg;
  private ImageLoader imageLoader;
  private boolean readme;
  private TableState table;
  private boolean outputNamedAnchor = true;

  public MarkdownToHtml(GitilesView view, Config cfg) {
    this.view = view;
    this.cfg = cfg;
  }

  public MarkdownToHtml setImageLoader(ImageLoader img) {
    imageLoader = img;
    return this;
  }

  public MarkdownToHtml setReadme(boolean readme) {
    this.readme = readme;
    return this;
  }

  /** Render the document AST to sanitized HTML. */
  public SanitizedContent toSoyHtml(RootNode node) {
    if (node == null) {
      return null;
    }

    toc.setRoot(node);
    node.accept(this);
    return html.toSoy();
  }

  @Override
  public void visit(RootNode node) {
    references.add(node);
    visitChildren(node);
  }

  @Override
  public void visit(TocNode node) {
    toc.format();
  }

  @Override
  public void visit(DivNode node) {
    html.open("div").attribute("class", node.getStyleName());
    visitChildren(node);
    html.close("div");
  }

  @Override
  public void visit(ColsNode node) {
    html.open("div").attribute("class", "cols");
    visitChildren(node);
    html.close("div");
  }

  @Override
  public void visit(ColsNode.Column node) {
    if (1 <= node.span && node.span <= ColsNode.GRID_WIDTH) {
      html.open("div").attribute("class", "col-" + node.span);
      visitChildren(node);
      html.close("div");
    }
  }

  @Override
  public void visit(IframeNode node) {
    if (HtmlBuilder.isValidHttpUri(node.src)
        && HtmlBuilder.isValidCssDimension(node.height)
        && HtmlBuilder.isValidCssDimension(node.width)
        && canRender(node)) {
      html.open("iframe")
          .attribute("src", node.src)
          .attribute("height", node.height)
          .attribute("width", node.width);
      if (!node.border) {
        html.attribute("class", "noborder");
      }
      html.close("iframe");
    }
  }

  private boolean canRender(IframeNode node) {
    String[] ok = cfg.getStringList("markdown", null, "allowiframe");
    if (ok.length == 1 && StringUtils.toBooleanOrNull(ok[0]) == Boolean.TRUE) {
      return true;
    }
    for (String m : ok) {
      if (m.equals(node.src) || (m.endsWith("/") && node.src.startsWith(m))) {
        return true;
      }
    }
    return false; // By default do not render iframe.
  }

  @Override
  public void visit(HeaderNode node) {
    String id = toc.idFromHeader(node);
    if (id != null) {
      html.open("a").attribute("name", id);
    }
    try {
      outputNamedAnchor = false;
      wrapChildren("h" + node.getLevel(), node);
    } finally {
      outputNamedAnchor = true;
    }
    if (id != null) {
      html.close("a");
    }
  }

  @Override
  public void visit(NamedAnchorNode node) {
    if (outputNamedAnchor) {
      html.open("a").attribute("name", node.name).close("a");
    }
  }

  @Override
  public void visit(ParaNode node) {
    wrapChildren("p", node);
  }

  @Override
  public void visit(BlockQuoteNode node) {
    wrapChildren("blockquote", node);
  }

  @Override
  public void visit(OrderedListNode node) {
    wrapChildren("ol", node);
  }

  @Override
  public void visit(BulletListNode node) {
    wrapChildren("ul", node);
  }

  @Override
  public void visit(ListItemNode node) {
    wrapChildren("li", node);
  }

  @Override
  public void visit(DefinitionListNode node) {
    wrapChildren("dl", node);
  }

  @Override
  public void visit(DefinitionNode node) {
    wrapChildren("dd", node);
  }

  @Override
  public void visit(DefinitionTermNode node) {
    wrapChildren("dt", node);
  }

  @Override
  public void visit(VerbatimNode node) {
    String lang = node.getType();
    String text = node.getText();

    html.open("pre").attribute("class", "code");
    text = printLeadingBlankLines(text);
    List<ParseResult> parsed = parse(lang, text);
    if (parsed != null) {
      int last = 0;
      for (ParseResult r : parsed) {
        span(null, text, last, r.getOffset());
        last = r.getOffset() + r.getLength();
        span(r.getStyleKeysString(), text, r.getOffset(), last);
      }
      if (last < text.length()) {
        span(null, text, last, text.length());
      }
    } else {
      html.appendAndEscape(text);
    }
    html.close("pre");
  }

  private String printLeadingBlankLines(String text) {
    int i = 0;
    while (i < text.length() && text.charAt(i) == '\n') {
      html.open("br");
      i++;
    }
    return text.substring(i);
  }

  private void span(String classes, String s, int start, int end) {
    if (end - start > 0) {
      if (Strings.isNullOrEmpty(classes)) {
        classes = Prettify.PR_PLAIN;
      }
      html.open("span").attribute("class", classes);
      html.appendAndEscape(s.substring(start, end));
      html.close("span");
    }
  }

  private List<ParseResult> parse(String lang, String text) {
    if (Strings.isNullOrEmpty(lang)) {
      return null;
    }
    try {
      return ThreadSafePrettifyParser.INSTANCE.parse(lang, text);
    } catch (StackOverflowError e) {
      return null;
    }
  }

  @Override
  public void visit(CodeNode node) {
    wrapText("code", node);
  }

  @Override
  public void visit(StrikeNode node) {
    wrapChildren("del", node);
  }

  @Override
  public void visit(StrongEmphSuperNode node) {
    if (node.isClosed()) {
      wrapChildren(node.isStrong() ? "strong" : "em", node);
    } else {
      // Unclosed (or unmatched) sequence is plain text.
      html.appendAndEscape(node.getChars());
      visitChildren(node);
    }
  }

  @Override
  public void visit(AutoLinkNode node) {
    String url = node.getText();
    html.open("a").attribute("href", href(url))
        .appendAndEscape(url)
        .close("a");
  }

  @Override
  public void visit(MailLinkNode node) {
    String addr = node.getText();
    html.open("a").attribute("href", "mailto:" + addr)
        .appendAndEscape(addr)
        .close("a");
  }

  @Override
  public void visit(WikiLinkNode node) {
    String text = node.getText();
    String path = text.replace(' ', '-') + ".md";
    html.open("a").attribute("href", href(path))
        .appendAndEscape(text)
        .close("a");
  }

  @Override
  public void visit(ExpLinkNode node) {
    html.open("a")
        .attribute("href", href(node.url))
        .attribute("title", node.title);
    visitChildren(node);
    html.close("a");
  }

  @Override
  public void visit(RefLinkNode node) {
    ReferenceNode ref = references.get(node.referenceKey, getInnerText(node));
    if (ref != null) {
      html.open("a")
          .attribute("href", href(ref.getUrl()))
          .attribute("title", ref.getTitle());
      visitChildren(node);
      html.close("a");
    } else {
      // Treat a broken RefLink as plain text.
      html.appendAndEscape("[");
      visitChildren(node);
      html.appendAndEscape("]");
    }
  }

  private String href(String url) {
    if (HtmlBuilder.isValidHttpUri(url)) {
      return url;
    }
    if (MarkdownUtil.isAbsolutePathToMarkdown(url)) {
      return GitilesView.doc().copyFrom(view).setPathPart(url).build().toUrl();
    }
    if (readme && !url.startsWith("../") && !url.startsWith("./")) {
      String dir = "";
      if (view.getPathPart() != null && view.getPathPart().endsWith("/")) {
        dir = view.getPathPart();
      } else if (view.getPathPart() != null) {
        dir = view.getPathPart() + '/';
      }
      return GitilesView.path().copyFrom(view)
          .setPathPart(dir + url)
          .build().toUrl();
    }
    return url;
  }

  @Override
  public void visit(ExpImageNode node) {
    html.open("img")
        .attribute("src", resolveImageUrl(node.url))
        .attribute("title", node.title)
        .attribute("alt", getInnerText(node));
  }

  @Override
  public void visit(RefImageNode node) {
    String alt = getInnerText(node);
    String url, title = alt;
    ReferenceNode ref = references.get(node.referenceKey, alt);
    if (ref != null) {
      url = resolveImageUrl(ref.getUrl());
      title = ref.getTitle();
    } else {
      // If reference is missing, insert a broken image.
      url = FilterImageDataUri.INSTANCE.getInnocuousOutput();
    }
    html.open("img")
        .attribute("src", url)
        .attribute("title", title)
        .attribute("alt", alt);
  }

  private String resolveImageUrl(String url) {
    if (imageLoader == null
        || url.startsWith("https://") || url.startsWith("http://")
        || url.startsWith("data:")) {
      return url;
    }
    return imageLoader.loadImage(url);
  }

  @Override
  public void visit(TableNode node) {
    table = new TableState(node);
    wrapChildren("table", node);
    table = null;
  }

  private void mustBeInsideTable(Node node) {
    checkState(table != null, "%s must be in table", node);
  }

  @Override
  public void visit(TableHeaderNode node) {
    mustBeInsideTable(node);
    table.inHeader = true;
    wrapChildren("thead", node);
    table.inHeader = false;
  }

  @Override
  public void visit(TableBodyNode node) {
    wrapChildren("tbody", node);
  }

  @Override
  public void visit(TableCaptionNode node) {
    wrapChildren("caption", node);
  }

  @Override
  public void visit(TableRowNode node) {
    mustBeInsideTable(node);
    table.startRow();
    wrapChildren("tr", node);
  }

  @Override
  public void visit(TableCellNode node) {
    mustBeInsideTable(node);
    String tag = table.inHeader ? "th" : "td";
    html.open(tag)
        .attribute("align", table.getAlign());
    if (node.getColSpan() > 1) {
      html.attribute("colspan", Integer.toString(node.getColSpan()));
    }
    visitChildren(node);
    html.close(tag);
    table.done(node);
  }

  @Override
  public void visit(TableColumnNode node) {
    // Not for output; should not be in the Visitor API.
  }

  @Override
  public void visit(TextNode node) {
    html.appendAndEscape(node.getText());
    // TODO(sop) printWithAbbreviations
  }

  @Override
  public void visit(SpecialTextNode node) {
    html.appendAndEscape(node.getText());
  }

  @Override
  public void visit(QuotedNode node) {
    switch (node.getType()) {
      case DoubleAngle:
        html.entity("&laquo;");
        visitChildren(node);
        html.entity("&raquo;");
        break;
      case Double:
        html.entity("&ldquo;");
        visitChildren(node);
        html.entity("&rdquo;");
        break;
      case Single:
        html.entity("&lsquo;");
        visitChildren(node);
        html.entity("&rsquo;");
        break;
      default:
        checkState(false, "unsupported quote %s", node.getType());
    }
  }

  @Override
  public void visit(SimpleNode node) {
    switch (node.getType()) {
      case Apostrophe:
        html.entity("&rsquo;");
        break;
      case Ellipsis:
        html.entity("&hellip;");
        break;
      case Emdash:
        html.entity("&mdash;");
        break;
      case Endash:
        html.entity("&ndash;");
        break;
      case HRule:
        html.open("hr");
        break;
      case Linebreak:
        html.open("br");
        break;
      case Nbsp:
        html.entity("&nbsp;");
        break;
      default:
        checkState(false, "unsupported node %s", node.getType());
    }
  }

  @Override
  public void visit(SuperNode node) {
    visitChildren(node);
  }

  @Override
  public void visit(Node node) {
    checkState(false, "node %s unsupported", node.getClass());
  }

  @Override
  public void visit(HtmlBlockNode node) {
    // Drop all HTML nodes.
  }

  @Override
  public void visit(InlineHtmlNode node) {
    // Drop all HTML nodes.
  }

  @Override
  public void visit(ReferenceNode node) {
    // Reference nodes are not printed; they only declare an item.
  }

  @Override
  public void visit(AbbreviationNode node) {
    // Abbreviation nodes are not printed; they only declare an item.
  }

  private void wrapText(String tag, TextNode node) {
    html.open(tag).appendAndEscape(node.getText()).close(tag);
  }

  private void wrapChildren(String tag, SuperNode node) {
    html.open(tag);
    visitChildren(node);
    html.close(tag);
  }

  private void visitChildren(Node node) {
    for (Node child : node.getChildren()) {
      child.accept(this);
    }
  }
}
