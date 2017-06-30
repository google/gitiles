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

import static com.google.gitiles.doc.MarkdownUtil.getInnerText;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gitiles.GitilesView;
import com.google.gitiles.ThreadSafePrettifyParser;
import com.google.gitiles.doc.html.HtmlBuilder;
import com.google.gitiles.doc.html.SoyHtmlBuilder;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.shared.restricted.EscapingConventions.FilterImageDataUri;
import com.google.template.soy.shared.restricted.EscapingConventions.FilterNormalizeUri;
import java.util.List;
import javax.annotation.Nullable;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.Block;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.node.Visitor;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevTree;
import prettify.parser.Prettify;
import syntaxhighlight.ParseResult;

/**
 * Formats parsed Markdown AST into HTML.
 *
 * <p>Callers must create a new instance for each document.
 */
public class MarkdownToHtml implements Visitor {
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String requestUri;
    private GitilesView view;
    private MarkdownConfig config;
    private String filePath;
    private ObjectReader reader;
    private RevTree root;

    Builder() {}

    public Builder setRequestUri(@Nullable String uri) {
      requestUri = uri;
      return this;
    }

    public Builder setGitilesView(@Nullable GitilesView view) {
      this.view = view;
      return this;
    }

    public Builder setConfig(@Nullable MarkdownConfig config) {
      this.config = config;
      return this;
    }

    public Builder setFilePath(@Nullable String filePath) {
      this.filePath = Strings.emptyToNull(filePath);
      return this;
    }

    public Builder setReader(ObjectReader reader) {
      this.reader = reader;
      return this;
    }

    public Builder setRootTree(RevTree tree) {
      this.root = tree;
      return this;
    }

    public MarkdownToHtml build() {
      return new MarkdownToHtml(this);
    }
  }

  private HtmlBuilder html;
  private TocFormatter toc;
  private final String requestUri;
  private final GitilesView view;
  private final MarkdownConfig config;
  private final String filePath;
  private final ImageLoader imageLoader;
  private boolean outputNamedAnchor = true;

  private MarkdownToHtml(Builder b) {
    requestUri = b.requestUri;
    view = b.view;
    config = b.config;
    filePath = b.filePath;
    imageLoader = newImageLoader(b);
  }

  private static ImageLoader newImageLoader(Builder b) {
    if (b.reader != null && b.view != null && b.config != null && b.root != null) {
      return new ImageLoader(b.reader, b.view, b.config, b.root);
    }
    return null;
  }

  /** Render the document AST to sanitized HTML. */
  public void renderToHtml(HtmlBuilder out, Node node) {
    if (node != null) {
      html = out;
      toc = new TocFormatter(html, 3);
      toc.setRoot(node);
      node.accept(this);
      html.finish();
      html = null;
      toc = null;
    }
  }

  /** Render the document AST to sanitized HTML. */
  public SanitizedContent toSoyHtml(Node node) {
    if (node != null) {
      SoyHtmlBuilder out = new SoyHtmlBuilder();
      renderToHtml(out, node);
      return out.toSoy();
    }
    return null;
  }

  @Override
  public void visit(Document node) {
    visitChildren(node);
  }

  private void visit(BlockNote node) {
    html.open("div").attribute("class", node.getClassName());
    Node f = node.getFirstChild();
    if (f == node.getLastChild() && f instanceof Paragraph) {
      // Avoid <p> inside <div> if there is only one <p>.
      visitChildren(f);
    } else {
      visitChildren(node);
    }
    html.close("div");
  }

  private void visit(MultiColumnBlock node) {
    html.open("div").attribute("class", "cols");
    visitChildren(node);
    html.close("div");
  }

  private void visit(MultiColumnBlock.Column node) {
    if (1 <= node.span && node.span <= MultiColumnBlock.GRID_WIDTH) {
      html.open("div").attribute("class", "col-" + node.span);
      visitChildren(node);
      html.close("div");
    }
  }

  private void visit(IframeBlock node) {
    if (HtmlBuilder.isValidHttpUri(node.src)
        && HtmlBuilder.isValidCssDimension(node.height)
        && HtmlBuilder.isValidCssDimension(node.width)
        && config != null
        && config.isIFrameAllowed(node.src)) {
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

  @Override
  public void visit(Heading node) {
    outputNamedAnchor = false;
    String tag = "h" + node.getLevel();
    html.open(tag);
    String id = toc.idFromHeader(node);
    if (id != null) {
      html.open("a")
          .attribute("class", "h")
          .attribute("name", id)
          .attribute("href", "#" + id)
          .open("span")
          .close("span")
          .close("a");
    }
    visitChildren(node);
    html.close(tag);
    outputNamedAnchor = true;
  }

  private void visit(NamedAnchor node) {
    if (outputNamedAnchor) {
      html.open("a").attribute("name", node.getName()).close("a");
    }
  }

  @Override
  public void visit(Paragraph node) {
    if (isInTightList(node)) {
      // Avoid unnecessary <p> tags within <ol><li> structures.
      visitChildren(node);
    } else {
      wrapChildren("p", node);
    }
  }

  private static boolean isInTightList(Paragraph c) {
    Block b = c.getParent(); // b is probably a ListItem
    if (b != null) {
      Block a = b.getParent();
      return a instanceof ListBlock && ((ListBlock) a).isTight();
    }
    return false;
  }

  @Override
  public void visit(BlockQuote node) {
    wrapChildren("blockquote", node);
  }

  @Override
  public void visit(OrderedList node) {
    html.open("ol");
    if (node.getStartNumber() != 1) {
      html.attribute("start", Integer.toString(node.getStartNumber()));
    }
    visitChildren(node);
    html.close("ol");
  }

  @Override
  public void visit(BulletList node) {
    wrapChildren("ul", node);
  }

  @Override
  public void visit(ListItem node) {
    wrapChildren("li", node);
  }

  @Override
  public void visit(FencedCodeBlock node) {
    codeInPre(node.getInfo(), node.getLiteral());
  }

  @Override
  public void visit(IndentedCodeBlock node) {
    codeInPre(null, node.getLiteral());
  }

  private void codeInPre(String lang, String text) {
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
  public void visit(Code node) {
    html.open("code").attribute("class", "code").appendAndEscape(node.getLiteral()).close("code");
  }

  @Override
  public void visit(Emphasis node) {
    wrapChildren("em", node);
  }

  @Override
  public void visit(StrongEmphasis node) {
    wrapChildren("strong", node);
  }

  @Override
  public void visit(Link node) {
    html.open("a")
        .attribute("href", href(node.getDestination()))
        .attribute("title", node.getTitle());
    visitChildren(node);
    html.close("a");
  }

  @VisibleForTesting
  String href(String target) {
    if (target.startsWith("#") || HtmlBuilder.isValidHttpUri(target)) {
      return target;
    } else if (target.startsWith("git:")) {
      if (HtmlBuilder.isValidGitUri(target)) {
        return target;
      }
      return FilterNormalizeUri.INSTANCE.getInnocuousOutput();
    }

    String anchor = "";
    int hash = target.indexOf('#');
    if (hash >= 0) {
      anchor = target.substring(hash);
      target = target.substring(0, hash);
    }

    String dest = PathResolver.resolve(filePath, target);
    if (dest == null || view == null) {
      return FilterNormalizeUri.INSTANCE.getInnocuousOutput();
    }

    GitilesView.Builder b;
    if (view.getType() == GitilesView.Type.ROOTED_DOC) {
      b = GitilesView.rootedDoc();
    } else {
      b = GitilesView.path();
    }
    dest = b.copyFrom(view).setPathPart(dest).build().toUrl();

    return PathResolver.relative(requestUri, dest) + anchor;
  }

  @Override
  public void visit(Image node) {
    html.open("img")
        .attribute("src", image(node.getDestination()))
        .attribute("title", node.getTitle())
        .attribute("alt", getInnerText(node));
  }

  String image(String dest) {
    if (HtmlBuilder.isValidHttpUri(dest) || HtmlBuilder.isImageDataUri(dest)) {
      return dest;
    } else if (imageLoader != null) {
      return imageLoader.inline(filePath, dest);
    }
    return FilterImageDataUri.INSTANCE.getInnocuousOutput();
  }

  public void visit(TableBlock node) {
    wrapChildren("table", node);
  }

  private void visit(TableRow node) {
    wrapChildren("tr", node);
  }

  private void visit(TableCell cell) {
    String tag = cell.isHeader() ? "th" : "td";
    html.open(tag);
    TableCell.Alignment alignment = cell.getAlignment();
    if (alignment != null) {
      html.attribute("align", toHtml(alignment));
    }
    visitChildren(cell);
    html.close(tag);
  }

  private static String toHtml(TableCell.Alignment alignment) {
    switch (alignment) {
      case LEFT:
        return "left";
      case CENTER:
        return "center";
      case RIGHT:
        return "right";
      default:
        throw new IllegalArgumentException("unsupported alignment " + alignment);
    }
  }

  private void visit(SmartQuoted node) {
    switch (node.getType()) {
      case DOUBLE:
        html.entity("&ldquo;");
        visitChildren(node);
        html.entity("&rdquo;");
        break;
      case SINGLE:
        html.entity("&lsquo;");
        visitChildren(node);
        html.entity("&rsquo;");
        break;
      default:
        throw new IllegalArgumentException("unsupported quote " + node.getType());
    }
  }

  @Override
  public void visit(Text node) {
    html.appendAndEscape(node.getLiteral());
  }

  @Override
  public void visit(SoftLineBreak node) {
    html.space();
  }

  @Override
  public void visit(HardLineBreak node) {
    html.open("br");
  }

  @Override
  public void visit(ThematicBreak thematicBreak) {
    html.open("hr");
  }

  @Override
  public void visit(HtmlInline node) {
    // Discard all HTML.
  }

  @Override
  public void visit(HtmlBlock node) {
    // Discard all HTML.
  }

  private void wrapChildren(String tag, Node node) {
    html.open(tag);
    visitChildren(node);
    html.close(tag);
  }

  private void visitChildren(Node node) {
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      c.accept(this);
    }
  }

  @Override
  public void visit(CustomNode node) {
    if (node instanceof NamedAnchor) {
      visit((NamedAnchor) node);
    } else if (node instanceof SmartQuoted) {
      visit((SmartQuoted) node);
    } else if (node instanceof Strikethrough) {
      wrapChildren("del", node);
    } else if (node instanceof TableBody) {
      wrapChildren("tbody", node);
    } else if (node instanceof TableCell) {
      visit((TableCell) node);
    } else if (node instanceof TableHead) {
      wrapChildren("thead", node);
    } else if (node instanceof TableRow) {
      visit((TableRow) node);
    } else {
      throw new IllegalArgumentException("cannot render " + node.getClass());
    }
  }

  @Override
  public void visit(CustomBlock node) {
    if (node instanceof BlockNote) {
      visit((BlockNote) node);
    } else if (node instanceof IframeBlock) {
      visit((IframeBlock) node);
    } else if (node instanceof MultiColumnBlock) {
      visit((MultiColumnBlock) node);
    } else if (node instanceof MultiColumnBlock.Column) {
      visit((MultiColumnBlock.Column) node);
    } else if (node instanceof TableBlock) {
      visit((TableBlock) node);
    } else if (node instanceof TocBlock) {
      toc.format();
    } else {
      throw new IllegalArgumentException("cannot render " + node.getClass());
    }
  }
}
