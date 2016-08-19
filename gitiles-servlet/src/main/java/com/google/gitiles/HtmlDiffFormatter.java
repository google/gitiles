// Copyright 2012 Google Inc. All Rights Reserved.
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

package com.google.gitiles;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.util.QuotedString.GIT_PATH;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringEscapeUtils;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.util.RawParseUtils;

/** Formats a unified format patch as UTF-8 encoded HTML. */
final class HtmlDiffFormatter extends DiffFormatter {
  private static final byte[] DIFF_BEGIN = "<pre class=\"u-pre Diff-unified\">".getBytes(UTF_8);
  private static final byte[] DIFF_END = "</pre>".getBytes(UTF_8);

  private static final byte[] HUNK_BEGIN = "<span class=\"Diff-hunk\">".getBytes(UTF_8);
  private static final byte[] HUNK_END = "</span>".getBytes(UTF_8);

  private static final byte[] LINE_INSERT_BEGIN = "<span class=\"Diff-insert\">".getBytes(UTF_8);
  private static final byte[] LINE_DELETE_BEGIN = "<span class=\"Diff-delete\">".getBytes(UTF_8);
  private static final byte[] LINE_CHANGE_BEGIN = "<span class=\"Diff-change\">".getBytes(UTF_8);
  private static final byte[] LINE_END = "</span>\n".getBytes(UTF_8);

  private final Renderer renderer;
  private final GitilesView view;
  private int fileIndex;
  private DiffEntry entry;

  HtmlDiffFormatter(Renderer renderer, GitilesView view, OutputStream out) {
    super(out);
    this.renderer = checkNotNull(renderer, "renderer");
    this.view = checkNotNull(view, "view");
  }

  @Override
  public void format(List<? extends DiffEntry> entries) throws IOException {
    for (fileIndex = 0; fileIndex < entries.size(); fileIndex++) {
      entry = entries.get(fileIndex);
      format(entry);
    }
  }

  @Override
  public void format(FileHeader hdr, RawText a, RawText b) throws IOException {
    int start = hdr.getStartOffset();
    int end = hdr.getEndOffset();
    if (!hdr.getHunks().isEmpty()) {
      end = hdr.getHunks().get(0).getStartOffset();
    }
    renderHeader(RawParseUtils.decode(hdr.getBuffer(), start, end));

    if (hdr.getPatchType() == PatchType.UNIFIED) {
      getOutputStream().write(DIFF_BEGIN);
      format(hdr.toEditList(), a, b);
      getOutputStream().write(DIFF_END);
    }
  }

  private void renderHeader(String header) throws IOException {
    int lf = header.indexOf('\n');
    String rest = 0 <= lf ? header.substring(lf + 1) : "";

    // Based on DiffFormatter.formatGitDiffFirstHeaderLine.
    List<Map<String, String>> parts = Lists.newArrayListWithCapacity(3);
    parts.add(ImmutableMap.of("text", "diff --git"));
    if (entry.getChangeType() != ChangeType.ADD) {
      parts.add(
          ImmutableMap.of(
              "text", GIT_PATH.quote(getOldPrefix() + entry.getOldPath()),
              "url", revisionUrl(view.getOldRevision(), entry.getOldPath())));
    } else {
      parts.add(ImmutableMap.of("text", GIT_PATH.quote(getOldPrefix() + entry.getNewPath())));
    }
    if (entry.getChangeType() != ChangeType.DELETE) {
      parts.add(
          ImmutableMap.of(
              "text", GIT_PATH.quote(getNewPrefix() + entry.getNewPath()),
              "url", revisionUrl(view.getRevision(), entry.getNewPath())));
    } else {
      parts.add(ImmutableMap.of("text", GIT_PATH.quote(getNewPrefix() + entry.getOldPath())));
    }

    getOutputStream()
        .write(
            renderer
                .newRenderer("gitiles.diffHeader")
                .setData(ImmutableMap.of("firstParts", parts, "rest", rest, "fileIndex", fileIndex))
                .render()
                .getBytes(UTF_8));
  }

  private String revisionUrl(Revision rev, String path) {
    return GitilesView.path()
        .copyFrom(view)
        .setOldRevision(Revision.NULL)
        .setRevision(Revision.named(rev.getId().name()))
        .setPathPart(path)
        .toUrl();
  }

  @Override
  protected void writeHunkHeader(int aStartLine, int aEndLine, int bStartLine, int bEndLine)
      throws IOException {
    getOutputStream().write(HUNK_BEGIN);
    // TODO(sop): If hunk header starts including method names, escape it.
    super.writeHunkHeader(aStartLine, aEndLine, bStartLine, bEndLine);
    getOutputStream().write(HUNK_END);
  }

  @Override
  protected void writeLine(char prefix, RawText text, int cur) throws IOException {
    // Manually render each line, rather than invoke a Soy template. This method
    // can be called thousands of times in a single request. Avoid unnecessary
    // overheads by formatting as-is.
    OutputStream out = getOutputStream();
    switch (prefix) {
      case '+':
        out.write(LINE_INSERT_BEGIN);
        break;
      case '-':
        out.write(LINE_DELETE_BEGIN);
        break;
      case ' ':
      default:
        out.write(LINE_CHANGE_BEGIN);
        break;
    }
    out.write(prefix);
    out.write(StringEscapeUtils.escapeHtml4(text.getString(cur)).getBytes(UTF_8));
    out.write(LINE_END);
  }
}
