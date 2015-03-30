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

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.net.HttpHeaders;
import com.google.gitiles.BaseServlet;
import com.google.gitiles.FormatType;
import com.google.gitiles.GitilesAccess;
import com.google.gitiles.GitilesView;
import com.google.gitiles.Renderer;
import com.google.gitiles.ViewFilter;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.pegdown.ast.RootNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DocServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  private static final String INDEX_MD = "index.md";
  private static final String NAVBAR_MD = "navbar.md";
  private static final String SOY_FILE = "Doc.soy";
  private static final String SOY_TEMPLATE = "gitiles.markdownDoc";

  // Generation of ETag logic. Bump this only if DocServlet logic changes
  // significantly enough to impact cached pages. Soy template and source
  // files are automatically hashed as part of the ETag.
  private static final int ETAG_GEN = 1;

  public DocServlet(GitilesAccess.Factory accessFactory, Renderer renderer) {
    super(renderer, accessFactory);
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    Config cfg = getAccess(req).getConfig();
    if (!cfg.getBoolean("markdown", "render", true)) {
      res.setStatus(SC_NOT_FOUND);
      return;
    }

    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);
    RevWalk rw = new RevWalk(repo);
    try {
      String path = view.getPathPart();
      RevTree root;
      try {
        root = rw.parseTree(view.getRevision().getId());
      } catch (IncorrectObjectTypeException e) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }

      SourceFile srcmd = findFile(rw, root, path);
      if (srcmd == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }

      SourceFile navmd = findFile(rw, root, NAVBAR_MD);
      String reqEtag = req.getHeader(HttpHeaders.IF_NONE_MATCH);
      String curEtag = etag(srcmd, navmd);
      if (reqEtag != null && reqEtag.equals(curEtag)) {
        res.setStatus(SC_NOT_MODIFIED);
        return;
      }

      view = view.toBuilder().setPathPart(srcmd.path).build();
      int inputLimit = cfg.getInt("markdown", "inputLimit", 5 << 20);
      RootNode doc = GitilesMarkdown.parseFile(
          view, srcmd.path,
          srcmd.read(rw.getObjectReader(), inputLimit));
      if (doc == null) {
        res.sendRedirect(GitilesView.show().copyFrom(view).toUrl());
        return;
      }

      RootNode nav = null;
      if (navmd != null) {
        nav = GitilesMarkdown.parseFile(
            view, navmd.path,
            navmd.read(rw.getObjectReader(), inputLimit));
        if (nav == null) {
          res.setStatus(SC_INTERNAL_SERVER_ERROR);
          return;
        }
      }

      int imageLimit = cfg.getInt("markdown", "imageLimit", 256 << 10);
      ImageLoader img = null;
      if (imageLimit > 0) {
        img = new ImageLoader(rw.getObjectReader(), view,
            root, srcmd.path, imageLimit);
      }

      res.setHeader(HttpHeaders.ETAG, curEtag);
      showDoc(req, res, view, cfg, img, nav, doc);
    } finally {
      rw.release();
    }
  }

  private String etag(SourceFile srcmd, SourceFile navmd) {
    byte[] b = new byte[Constants.OBJECT_ID_LENGTH];
    Hasher h = Hashing.sha1().newHasher();
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

  @Override
  protected void setCacheHeaders(HttpServletResponse res) {
    long now = System.currentTimeMillis();
    res.setDateHeader(HttpHeaders.EXPIRES, now);
    res.setDateHeader(HttpHeaders.DATE, now);
    res.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate");
  }

  private void showDoc(HttpServletRequest req, HttpServletResponse res,
      GitilesView view, Config cfg, ImageLoader img,
      RootNode nav, RootNode doc) throws IOException {
    Map<String, Object> data = new HashMap<>();
    data.putAll(Navbar.bannerSoyData(view, img, nav));
    data.put("pageTitle", MoreObjects.firstNonNull(
        MarkdownUtil.getTitle(doc),
        view.getPathPart()));
    data.put("sourceUrl", GitilesView.show().copyFrom(view).toUrl());
    data.put("logUrl", GitilesView.log().copyFrom(view).toUrl());
    data.put("blameUrl", GitilesView.blame().copyFrom(view).toUrl());
    data.put("navbarHtml", new MarkdownToHtml(view, cfg).toSoyHtml(nav));
    data.put("bodyHtml", new MarkdownToHtml(view, cfg)
        .setImageLoader(img)
        .toSoyHtml(doc));

    String page = renderer.render(SOY_TEMPLATE, data);
    byte[] raw = page.getBytes(UTF_8);
    res.setContentType(FormatType.HTML.getMimeType());
    res.setCharacterEncoding(UTF_8.name());
    setCacheHeaders(res);
    if (acceptsGzipEncoding(req)) {
      res.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
      raw = gzip(raw);
    }
    res.setContentLength(raw.length);
    res.setStatus(HttpServletResponse.SC_OK);
    res.getOutputStream().write(raw);
  }

  private static SourceFile findFile(RevWalk rw, RevTree root, String path) throws IOException {
    if (Strings.isNullOrEmpty(path)) {
      path = INDEX_MD;
    }

    ObjectReader reader = rw.getObjectReader();
    TreeWalk tw = TreeWalk.forPath(reader, path, root);
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
      return new SourceFile(path, tw.getObjectId(0));
    }
    return null;
  }

  private static boolean findIndexFile(TreeWalk tw) throws IOException {
    tw.enterSubtree();
    while (tw.next()) {
      if ((tw.getRawMode(0) & TYPE_MASK) == TYPE_FILE
          && INDEX_MD.equals(tw.getNameString())) {
        return true;
      }
    }
    return false;
  }

  private static class SourceFile {
    final String path;
    final ObjectId id;

    SourceFile(String path, ObjectId id) {
      this.path = path;
      this.id = id;
    }

    String read(ObjectReader reader, int inputLimit) throws IOException {
      ObjectLoader obj = reader.open(id, OBJ_BLOB);
      byte[] raw = obj.getCachedBytes(inputLimit);
      return RawParseUtils.decode(raw);
    }
  }
}
