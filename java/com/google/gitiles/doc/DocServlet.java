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

import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.net.HttpHeaders;
import com.google.gitiles.BaseServlet;
import com.google.gitiles.GitilesAccess;
import com.google.gitiles.GitilesRequestFailureException;
import com.google.gitiles.GitilesRequestFailureException.FailureReason;
import com.google.gitiles.GitilesView;
import com.google.gitiles.Renderer;
import com.google.gitiles.ViewFilter;
import com.google.gitiles.doc.html.StreamHtmlBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.commonmark.node.Node;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocServlet extends BaseServlet {
  private static final Logger log = LoggerFactory.getLogger(DocServlet.class);
  private static final long serialVersionUID = 1L;

  private static final String INDEX_MD = "index.md";
  private static final String NAVBAR_MD = "navbar.md";
  private static final String SOY_FILE = "Doc.soy";
  private static final String SOY_TEMPLATE = "gitiles.markdownDoc";

  // Generation of ETag logic. Bump this only if DocServlet logic changes
  // significantly enough to impact cached pages. Soy template and source
  // files are automatically hashed as part of the ETag.
  private static final int ETAG_GEN = 5;

  private final HtmlSanitizer.Factory htmlSanitizer;

  public DocServlet(GitilesAccess.Factory accessFactory, Renderer renderer) {
    this(accessFactory, renderer, HtmlSanitizer.DISABLED_FACTORY);
  }

  public DocServlet(
      GitilesAccess.Factory accessFactory, Renderer renderer, HtmlSanitizer.Factory htmlSanitizer) {
    super(renderer, accessFactory);
    this.htmlSanitizer = htmlSanitizer;
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    MarkdownConfig cfg = MarkdownConfig.get(getAccess(req).getConfig());
    if (!cfg.render) {
      throw new GitilesRequestFailureException(FailureReason.MARKDOWN_NOT_ENABLED);
    }

    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);
    try (RevWalk rw = new RevWalk(repo)) {
      ObjectReader reader = rw.getObjectReader();
      String path = view.getPathPart();
      RevTree root;
      try {
        root = rw.parseTree(view.getRevision().getId());
      } catch (IncorrectObjectTypeException e) {
        throw new GitilesRequestFailureException(FailureReason.INCORRECT_OBJECT_TYPE);
      }

      MarkdownFile srcmd = findFile(rw, root, path);
      if (srcmd == null) {
        throw new GitilesRequestFailureException(FailureReason.OBJECT_NOT_FOUND);
      }

      MarkdownFile navmd = findNavbar(rw, root, path);
      String curEtag = etag(srcmd, navmd);
      if (etagMatch(req, curEtag)) {
        res.setStatus(SC_NOT_MODIFIED);
        return;
      }

      view = view.toBuilder().setPathPart(srcmd.path).build();
      try {
        srcmd.read(reader, cfg);
        if (navmd != null) {
          navmd.read(reader, cfg);
        }
      } catch (LargeObjectException.ExceedsLimit errBig) {
        fileTooBig(res, view, errBig);
        return;
      }

      MarkdownToHtml.Builder fmt =
          MarkdownToHtml.builder()
              .setConfig(cfg)
              .setGitilesView(view)
              .setRequestUri(req.getRequestURI())
              .setReader(reader)
              .setRootTree(root)
              .setHtmlSanitizer(htmlSanitizer.create(req));
      Navbar navbar = createNavbar(cfg, fmt, navmd);
      res.setHeader(HttpHeaders.ETAG, curEtag);
      showDoc(req, res, view, fmt, navbar, srcmd);
    }
  }

  private static boolean etagMatch(HttpServletRequest req, String etag) {
    String reqEtag = req.getHeader(HttpHeaders.IF_NONE_MATCH);
    return reqEtag != null && reqEtag.equals(etag);
  }

  private String etag(MarkdownFile srcmd, @Nullable MarkdownFile navmd) {
    byte[] b = new byte[Constants.OBJECT_ID_LENGTH];
    Hasher h = Hashing.murmur3_128().newHasher();
    h.putInt(ETAG_GEN);

    renderer.getTemplateHash(SOY_FILE).writeBytesTo(b, 0, b.length);
    h.putBytes(b);

    if (navmd != null) {
      navmd.id.copyRawTo(b, 0);
      h.putBytes(b);
    }

    srcmd.id.copyRawTo(b, 0);
    h.putBytes(b);
    return h.hash().toString();
  }

  private MarkdownFile findNavbar(RevWalk rw, RevTree root, String path) throws IOException {
    if (!Strings.isNullOrEmpty(path)) {
      // Traverse up the path until we find a NAVBAR_MD.
      StringBuilder pathRemaining = new StringBuilder(path);
      while (pathRemaining.length() > 0) {
        int lastPathSeparatorIndex = pathRemaining.lastIndexOf("/");
        pathRemaining.setLength(lastPathSeparatorIndex + 1);
        MarkdownFile navmd = findFile(rw, root, pathRemaining.toString() + NAVBAR_MD);
        if (navmd != null) {
          return navmd;
        }
        pathRemaining.setLength(Math.max(lastPathSeparatorIndex, 0));
      }
      return null;
    }

    return findFile(rw, root, NAVBAR_MD);
  }

  private void showDoc(
      HttpServletRequest req,
      HttpServletResponse res,
      GitilesView view,
      MarkdownToHtml.Builder fmt,
      Navbar navbar,
      MarkdownFile srcFile)
      throws IOException {
    Map<String, Object> data = new HashMap<>();
    data.putAll(navbar.toSoyData());

    MarkdownConfig cfg = navbar.getConfig();
    Node doc = GitilesMarkdown.parse(cfg, srcFile.consumeContent());
    data.put("pageTitle", pageTitle(doc, srcFile));
    if (view.getType() != GitilesView.Type.ROOTED_DOC) {
      data.put("sourceUrl", GitilesView.show().copyFrom(view).toUrl());
      data.put("logUrl", GitilesView.log().copyFrom(view).toUrl());
      data.put("blameUrl", GitilesView.blame().copyFrom(view).toUrl());
    }
    if (cfg.analyticsId != null) {
      data.put("analyticsId", cfg.analyticsId);
    }

    try (OutputStream out = startRenderCompressedStreamingHtml(req, res, SOY_TEMPLATE, data)) {
      Writer w = newWriter(out, res);
      fmt.setConfig(cfg)
          .setFilePath(srcFile.path)
          .build()
          .renderToHtml(new StreamHtmlBuilder(w), doc);
      w.flush();
    } catch (RuntimeIOException e) {
      Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
      throw e;
    }
  }

  private static Navbar createNavbar(
      MarkdownConfig cfg, MarkdownToHtml.Builder fmt, @Nullable MarkdownFile navFile) {
    Navbar navbar = new Navbar().setConfig(cfg);
    if (navFile != null) {
      navbar
          .setFormatter(fmt.setFilePath(navFile.path).build())
          .setMarkdown(navFile.consumeContent());
    }
    return navbar;
  }

  private static String pageTitle(Node doc, MarkdownFile srcFile) {
    String title = MarkdownUtil.getTitle(doc);
    return MoreObjects.firstNonNull(title, srcFile.path);
  }

  @Nullable
  private static MarkdownFile findFile(RevWalk rw, RevTree root, String path) throws IOException {
    if (Strings.isNullOrEmpty(path)) {
      path = INDEX_MD;
    }

    ObjectReader reader = rw.getObjectReader();
    try (TreeWalk tw = TreeWalk.forPath(reader, path, root)) {
      if (tw == null) {
        return null;
      }
      if ((tw.getRawMode(0) & TYPE_MASK) == TYPE_TREE) {
        if (findIndexFile(tw)) {
          path = tw.getPathString();
        } else {
          return null;
        }
      }
      if ((tw.getRawMode(0) & TYPE_MASK) == TYPE_FILE) {
        if (!path.endsWith(".md")) {
          return null;
        }
        return new MarkdownFile(path, tw.getObjectId(0));
      }
      return null;
    }
  }

  private static boolean findIndexFile(TreeWalk tw) throws IOException {
    tw.enterSubtree();
    while (tw.next()) {
      if ((tw.getRawMode(0) & TYPE_MASK) == TYPE_FILE && INDEX_MD.equals(tw.getNameString())) {
        return true;
      }
    }
    return false;
  }

  private static void fileTooBig(
      HttpServletResponse res, GitilesView view, LargeObjectException.ExceedsLimit errBig)
      throws IOException {
    if (view.getType() == GitilesView.Type.ROOTED_DOC) {
      throw new GitilesRequestFailureException(FailureReason.OBJECT_TOO_LARGE);
    } else {
      res.sendRedirect(GitilesView.show().copyFrom(view).toUrl());
    }
  }

  private static class MarkdownFile {
    final String path;
    final ObjectId id;
    byte[] content;

    MarkdownFile(String path, ObjectId id) {
      this.path = path;
      this.id = id;
    }

    void read(ObjectReader reader, MarkdownConfig cfg) throws IOException {
      content = reader.open(id, OBJ_BLOB).getCachedBytes(cfg.inputLimit);
    }

    byte[] consumeContent() {
      byte[] c = content;
      content = null;
      return c;
    }
  }
}
