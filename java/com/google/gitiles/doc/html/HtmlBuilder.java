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

package com.google.gitiles.doc.html;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.types.SafeHtml;
import com.google.gitiles.doc.RuntimeIOException;
import com.google.template.soy.shared.restricted.EscapingConventions.EscapeHtml;
import com.google.template.soy.shared.restricted.EscapingConventions.FilterImageDataUri;
import com.google.template.soy.shared.restricted.EscapingConventions.FilterNormalizeUri;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Builds a document fragment using a restricted subset of HTML.
 *
 * <p>Most attributes are rejected ({@code style}, {@code onclick}, ...) by throwing
 * IllegalArgumentException if the caller attempts to add them to a pending element.
 *
 * <p>Useful but critical attributes like {@code href} on anchors or {@code src} on img permit only
 * safe subset of URIs, primarily {@code http://}, {@code https://}, and for image src {@code
 * data:image/*;base64,...}.
 *
 * <p>See concrete subclasses {@link SoyHtmlBuilder} and {@link StreamHtmlBuilder}.
 */
public abstract class HtmlBuilder {
  private static final ImmutableSet<String> ALLOWED_TAGS =
      ImmutableSet.of(
          "h1",
          "h2",
          "h3",
          "h4",
          "h5",
          "h6",
          "a",
          "div",
          "img",
          "p",
          "blockquote",
          "pre",
          "ol",
          "ul",
          "li",
          "dl",
          "dd",
          "dt",
          "del",
          "em",
          "strong",
          "code",
          "br",
          "hr",
          "table",
          "thead",
          "tbody",
          "caption",
          "tr",
          "th",
          "td",
          "iframe",
          "span");

  private static final ImmutableSet<String> ALLOWED_ATTRIBUTES =
      ImmutableSet.of("id", "class", "role");

  private static final ImmutableSet<String> SELF_CLOSING_TAGS = ImmutableSet.of("img", "br", "hr");

  private static final FilterNormalizeUri URI = FilterNormalizeUri.INSTANCE;
  private static final FilterImageDataUri IMAGE_DATA = FilterImageDataUri.INSTANCE;

  private static final Pattern GIT_URI =
      Pattern.compile(
          "^"
              +
              // Reject paths containing /../ or ending in /..
              "(?![^#?]*/(?:\\.|%2E){2}(?:[/?#]|\\z))"
              +
              // Accept git://host/path
              "git://[^/]+/.+",
          Pattern.CASE_INSENSITIVE);

  public static boolean isValidCssDimension(String val) {
    return val != null && val.matches("(?:[1-9][0-9]*px|100%|[1-9][0-9]?%)");
  }

  public static boolean isValidHttpUri(String val) {
    return (val.startsWith("https://") || val.startsWith("http://") || val.startsWith("//"))
        && URI.getValueFilter().matcher(val).find();
  }

  /** Check if URL is valid for {@code <img src="data:image/*;base64,...">}. */
  public static boolean isImageDataUri(String url) {
    return IMAGE_DATA.getValueFilter().matcher(url).find();
  }

  public static boolean isValidGitUri(String val) {
    return GIT_URI.matcher(val).find();
  }

  private final Appendable htmlBuf;
  private final Appendable textBuf;
  private String tag;

  HtmlBuilder(Appendable out) {
    htmlBuf = out;
    textBuf = EscapeHtml.INSTANCE.escape(htmlBuf);
  }

  /** Begin a new HTML tag. */
  public HtmlBuilder open(String tagName) {
    checkArgument(ALLOWED_TAGS.contains(tagName), "invalid HTML tag %s", tagName);
    finishActiveTag();
    try {
      htmlBuf.append('<').append(tagName);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    tag = tagName;
    return this;
  }

  /** Filter and append an attribute to the last tag. */
  public HtmlBuilder attribute(String att, String val) {
    if (Strings.isNullOrEmpty(val)) {
      return this;
    } else if ("href".equals(att) && "a".equals(tag)) {
      val = anchorHref(val);
    } else if ("src".equals(att) && "img".equals(tag)) {
      val = imgSrc(val);
    } else if ("src".equals(att) && "iframe".equals(tag)) {
      if (!isValidHttpUri(val)) {
        return this;
      }
      val = URI.escape(val);
    } else if (("height".equals(att) || "width".equals(att)) && "iframe".equals(tag)) {
      val = isValidCssDimension(val) ? val : "250px";
    } else if ("alt".equals(att) && "img".equals(tag)) {
      // allow
    } else if ("title".equals(att) && ("img".equals(tag) || "a".equals(tag))) {
      // allow
    } else if ("name".equals(att) && "a".equals(tag)) {
      // allow
    } else if ("start".equals(att) && "ol".equals(tag)) {
      // allow
    } else if (("colspan".equals(att) || "align".equals(att))
        && ("td".equals(tag) || "th".equals(tag))) {
      // allow
    } else {
      checkState(tag != null, "tag must be pending");
      checkArgument(ALLOWED_ATTRIBUTES.contains(att), "invalid attribute %s", att);
    }

    try {
      htmlBuf.append(' ').append(att).append("=\"");
      textBuf.append(val);
      htmlBuf.append('"');
      return this;
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private String anchorHref(String val) {
    if (URI.getValueFilter().matcher(val).find() || isValidGitUri(val)) {
      return URI.escape(val);
    }
    return URI.getInnocuousOutput();
  }

  private static String imgSrc(String val) {
    if (isValidHttpUri(val)) {
      return URI.escape(val);
    }
    if (isImageDataUri(val)) {
      return val; // pass through data:image/*;base64,...
    }
    return IMAGE_DATA.getInnocuousOutput();
  }

  private void finishActiveTag() {
    if (tag != null) {
      try {
        if (SELF_CLOSING_TAGS.contains(tag)) {
          htmlBuf.append(" />");
        } else {
          htmlBuf.append('>');
        }
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
      tag = null;
    }
  }

  /** Close an open tag with {@code </tag>} */
  public HtmlBuilder close(String tag) {
    checkArgument(
        ALLOWED_TAGS.contains(tag) && !SELF_CLOSING_TAGS.contains(tag), "invalid HTML tag %s", tag);

    finishActiveTag();
    try {
      htmlBuf.append("</").append(tag).append('>');
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    return this;
  }

  /** Escapes and appends any text as a child of the current element. */
  public HtmlBuilder appendAndEscape(CharSequence in) {
    try {
      finishActiveTag();
      textBuf.append(in);
      return this;
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /** Append a space outside of an element. */
  public HtmlBuilder space() {
    finishActiveTag();
    try {
      htmlBuf.append(' ');
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    return this;
  }

  private static final Pattern HTML_ENTITY = Pattern.compile("&[a-z]+;");

  /** Append constant entity reference like {@code &nbsp;}. */
  public void entity(String entity) {
    checkArgument(HTML_ENTITY.matcher(entity).matches(), "invalid entity %s", entity);
    finishActiveTag();
    try {
      htmlBuf.append(entity);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /** Append a previously determined to be safe HTML fragment. */
  public void append(SafeHtml html) {
    checkNotNull(html, "SafeHtml");
    finishActiveTag();
    try {
      htmlBuf.append(html.getSafeHtmlString());
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /** Finish the document. */
  public void finish() {
    finishActiveTag();
  }
}
