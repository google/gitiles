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
import static com.google.gitiles.TreeSoyData.resolveTargetUrl;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serves an HTML page with detailed information about a path within a tree. */
// TODO(dborowitz): Handle non-UTF-8 names.
public class PathServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(PathServlet.class);

  static final String MODE_HEADER = "X-Gitiles-Path-Mode";
  static final String TYPE_HEADER = "X-Gitiles-Object-Type";

  /**
   * Submodule URLs where we know there is a web page if the user visits the repository URL verbatim
   * in a web browser.
   */
  private static final Pattern VERBATIM_SUBMODULE_URL_PATTERN =
      Pattern.compile(
          "^("
              + Joiner.on('|')
                  .join(
                      "https?://[^.]+.googlesource.com/.*",
                      "https?://[^.]+.googlecode.com/.*",
                      "https?://code.google.com/p/.*",
                      "https?://github.com/.*")
              + ")$",
          Pattern.CASE_INSENSITIVE);

  static final String AUTODIVE_PARAM = "autodive";
  static final String NO_AUTODIVE_VALUE = "0";

  enum FileType {
    TREE(FileMode.TREE),
    SYMLINK(FileMode.SYMLINK),
    REGULAR_FILE(FileMode.REGULAR_FILE),
    EXECUTABLE_FILE(FileMode.EXECUTABLE_FILE),
    GITLINK(FileMode.GITLINK);

    private final FileMode mode;

    FileType(FileMode mode) {
      this.mode = mode;
    }

    static FileType forEntry(TreeWalk tw) {
      int mode = tw.getRawMode(0);
      for (FileType type : values()) {
        if (type.mode.equals(mode)) {
          return type;
        }
      }
      return null;
    }
  }

  private final GitilesUrls urls;

  public PathServlet(GitilesAccess.Factory accessFactory, Renderer renderer, GitilesUrls urls) {
    super(renderer, accessFactory);
    this.urls = checkNotNull(urls, "urls");
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    try (RevWalk rw = new RevWalk(repo);
        WalkResult wr = WalkResult.forPath(rw, view, false)) {
      if (wr == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }
      switch (wr.type) {
        case TREE:
          showTree(req, res, wr);
          break;
        case SYMLINK:
          showSymlink(req, res, wr);
          break;
        case REGULAR_FILE:
        case EXECUTABLE_FILE:
          showFile(req, res, wr);
          break;
        case GITLINK:
          showGitlink(req, res, wr);
          break;
        default:
          log.error("Bad file type: {}", wr.type);
          res.setStatus(SC_NOT_FOUND);
          break;
      }
    } catch (LargeObjectException e) {
      res.setStatus(SC_INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  protected void doGetText(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    try (RevWalk rw = new RevWalk(repo);
        WalkResult wr = WalkResult.forPath(rw, view, false)) {
      if (wr == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }

      // Write base64 as plain text without modifying any other headers, under
      // the assumption that any hint we can give to a browser that this is
      // base64 data might cause it to try to decode it and render as HTML,
      // which would be bad.
      switch (wr.type) {
        case SYMLINK:
        case REGULAR_FILE:
        case EXECUTABLE_FILE:
          writeBlobText(req, res, wr);
          break;
        case TREE:
          writeTreeText(req, res, wr);
          break;
        case GITLINK:
        default:
          renderTextError(req, res, SC_NOT_FOUND, "Not a file");
          break;
      }
    } catch (LargeObjectException e) {
      res.setStatus(SC_INTERNAL_SERVER_ERROR);
    }
  }

  public static void setModeHeader(HttpServletResponse res, FileType type) {
    res.setHeader(MODE_HEADER, String.format("%06o", type.mode.getBits()));
  }

  public static void setTypeHeader(HttpServletResponse res, int type) {
    res.setHeader(TYPE_HEADER, Constants.typeString(type));
  }

  private void writeBlobText(HttpServletRequest req, HttpServletResponse res, WalkResult wr)
      throws IOException {
    setTypeHeader(res, wr.type.mode.getObjectType());
    setModeHeader(res, wr.type);
    try (Writer writer = startRenderText(req, res);
        OutputStream out = BaseEncoding.base64().encodingStream(writer)) {
      wr.getObjectReader().open(wr.id).copyTo(out);
    }
  }

  private void writeTreeText(HttpServletRequest req, HttpServletResponse res, WalkResult wr)
      throws IOException {
    setTypeHeader(res, wr.type.mode.getObjectType());
    setModeHeader(res, wr.type);

    try (Writer writer = startRenderText(req, res);
        OutputStream out = BaseEncoding.base64().encodingStream(writer)) {
      // Match git ls-tree format.
      while (wr.tw.next()) {
        FileMode mode = wr.tw.getFileMode(0);
        out.write(Constants.encode(String.format("%06o", mode.getBits())));
        out.write(' ');
        out.write(Constants.encode(Constants.typeString(mode.getObjectType())));
        out.write(' ');
        wr.tw.getObjectId(0).copyTo(out);
        out.write('\t');
        out.write(Constants.encode(QuotedString.GIT_PATH.quote(wr.tw.getNameString())));
        out.write('\n');
      }
    }
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    String longStr = req.getParameter("long");
    boolean includeSizes =
        (longStr != null)
            && (longStr.isEmpty() || Boolean.TRUE.equals(StringUtils.toBooleanOrNull(longStr)));

    String recursiveStr = req.getParameter("recursive");
    boolean recursive =
        (recursiveStr != null)
            && (recursiveStr.isEmpty()
                || Boolean.TRUE.equals(StringUtils.toBooleanOrNull(recursiveStr)));

    try (RevWalk rw = new RevWalk(repo);
        WalkResult wr = WalkResult.forPath(rw, view, recursive)) {
      if (wr == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }
      switch (wr.type) {
        case REGULAR_FILE:
          renderJson(
              req,
              res,
              FileJsonData.toJsonData(
                  wr.id, view.getRepositoryName(), view.getRevision().getName(), wr.path),
              FileJsonData.File.class);
          break;
        case TREE:
          renderJson(
              req,
              res,
              TreeJsonData.toJsonData(wr.id, wr.tw, includeSizes, recursive),
              TreeJsonData.Tree.class);
          break;
        case EXECUTABLE_FILE:
        case GITLINK:
        case SYMLINK:
        default:
          res.setStatus(SC_NOT_FOUND);
          break;
      }
    } catch (LargeObjectException e) {
      res.setStatus(SC_INTERNAL_SERVER_ERROR);
    }
  }

  private static RevTree getRoot(GitilesView view, RevWalk rw) throws IOException {
    RevObject obj = rw.peel(rw.parseAny(view.getRevision().getId()));
    switch (obj.getType()) {
      case OBJ_COMMIT:
        return ((RevCommit) obj).getTree();
      case OBJ_TREE:
        return (RevTree) obj;
      default:
        return null;
    }
  }

  private static class AutoDiveFilter extends TreeFilter {
    /** @see GitilesView#getBreadcrumbs(List) */
    List<Boolean> hasSingleTree;

    private final byte[] pathRaw;
    private int count;
    private boolean done;

    AutoDiveFilter(String pathStr) {
      hasSingleTree = Lists.newArrayList();
      pathRaw = Constants.encode(pathStr);
    }

    @Override
    public boolean include(TreeWalk tw)
        throws MissingObjectException, IncorrectObjectTypeException, IOException {

      count++;
      int cmp = tw.isPathPrefix(pathRaw, pathRaw.length);
      if (cmp > 0) {
        throw StopWalkException.INSTANCE;
      }
      boolean include;
      if (cmp == 0) {
        if (!isDone(tw)) {
          hasSingleTree.add(hasSingleTreeEntry(tw));
        }
        include = true;
      } else {
        include = false;
      }
      if (tw.isSubtree()) {
        count = 0;
      }
      return include;
    }

    private boolean hasSingleTreeEntry(TreeWalk tw) throws IOException {
      if (count != 1 || !FileMode.TREE.equals(tw.getRawMode(0))) {
        return false;
      }
      CanonicalTreeParser p = new CanonicalTreeParser();
      p.reset(tw.getObjectReader(), tw.getObjectId(0));
      p.next();
      return p.eof();
    }

    @Override
    public boolean shouldBeRecursive() {
      return Bytes.indexOf(pathRaw, (byte) '/') >= 0;
    }

    @Override
    public TreeFilter clone() {
      return this;
    }

    private boolean isDone(TreeWalk tw) {
      if (!done) {
        done = pathRaw.length == tw.getPathLength();
      }
      return done;
    }
  }

  /**
   * Encapsulate the result of walking to a single tree.
   *
   * <p>Unlike {@link TreeWalk} itself, supports positioning at the root tree. Includes information
   * to help the auto-dive routine as well.
   */
  private static class WalkResult implements AutoCloseable {
    private static WalkResult recursivePath(RevWalk rw, GitilesView view) throws IOException {
      RevTree root = getRoot(view, rw);
      String path = view.getPathPart();

      TreeWalk tw;
      if (!path.isEmpty()) {
        try (TreeWalk toRoot = TreeWalk.forPath(rw.getObjectReader(), path, root)) {
          if (toRoot == null) {
            return null;
          }

          ObjectId treeSHA = toRoot.getObjectId(0);

          ObjectLoader treeLoader = rw.getObjectReader().open(treeSHA);
          if (treeLoader.getType() != Constants.OBJ_TREE) {
            return null;
          }

          tw = new TreeWalk(rw.getObjectReader());
          tw.addTree(treeSHA);
        }
      } else {
        tw = new TreeWalk(rw.getObjectReader());
        tw.addTree(root);
      }

      tw.setRecursive(true);
      return new WalkResult(tw, path, root, root, FileType.TREE, ImmutableList.<Boolean>of());
    }

    private static WalkResult forPath(RevWalk rw, GitilesView view, boolean recursive)
        throws IOException {
      if (recursive) {
        return recursivePath(rw, view);
      }

      RevTree root = getRoot(view, rw);
      String path = view.getPathPart();
      try (TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
        tw.addTree(root);
        tw.setRecursive(false);
        if (path.isEmpty()) {
          return new WalkResult(tw, path, root, root, FileType.TREE, ImmutableList.<Boolean>of());
        }
        AutoDiveFilter f = new AutoDiveFilter(path);
        tw.setFilter(f);
        while (tw.next()) {
          if (f.isDone(tw)) {
            FileType type = FileType.forEntry(tw);
            ObjectId id = tw.getObjectId(0);
            if (type == FileType.TREE) {
              tw.enterSubtree();
              tw.setRecursive(false);
            }
            return new WalkResult(tw, path, root, id, type, f.hasSingleTree);
          } else if (tw.isSubtree()) {
            tw.enterSubtree();
          }
        }
      } catch (IOException | RuntimeException e) {
        // Fallthrough.
      }
      return null;
    }

    private final TreeWalk tw;
    private final String path;
    private final RevTree root;
    private final ObjectId id;
    private final FileType type;
    private final List<Boolean> hasSingleTree;

    private WalkResult(
        TreeWalk tw,
        String path,
        RevTree root,
        ObjectId objectId,
        FileType type,
        List<Boolean> hasSingleTree) {
      this.tw = tw;
      this.path = path;
      this.root = root;
      this.id = objectId;
      this.type = type;
      this.hasSingleTree = hasSingleTree;
    }

    private ObjectReader getObjectReader() {
      return tw.getObjectReader();
    }

    @Override
    public void close() {
      tw.close();
    }
  }

  private void showTree(HttpServletRequest req, HttpServletResponse res, WalkResult wr)
      throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Config cfg = getAccess(req).getConfig();
    List<String> autodive = view.getParameters().get(AUTODIVE_PARAM);
    if (autodive.size() != 1 || !NO_AUTODIVE_VALUE.equals(autodive.get(0))) {
      byte[] path = Constants.encode(view.getPathPart());
      ObjectReader reader = wr.getObjectReader();
      CanonicalTreeParser child = getOnlyChildSubtree(reader, wr.id, path);
      if (child != null) {
        while (true) {
          path = new byte[child.getEntryPathLength()];
          System.arraycopy(child.getEntryPathBuffer(), 0, path, 0, child.getEntryPathLength());
          CanonicalTreeParser next = getOnlyChildSubtree(reader, child.getEntryObjectId(), path);
          if (next == null) {
            break;
          }
          child = next;
        }
        res.sendRedirect(
            GitilesView.path()
                .copyFrom(view)
                .setPathPart(
                    RawParseUtils.decode(child.getEntryPathBuffer(), 0, child.getEntryPathLength()))
                .toUrl());
        return;
      }
    }
    // TODO(sop): Allow caching trees by SHA-1 when no S cookie is sent.
    renderHtml(
        req,
        res,
        "gitiles.pathDetail",
        ImmutableMap.of(
            "title", !view.getPathPart().isEmpty() ? view.getPathPart() : "/",
            "breadcrumbs", view.getBreadcrumbs(wr.hasSingleTree),
            "type", FileType.TREE.toString(),
            "data",
                new TreeSoyData(wr.getObjectReader(), view, cfg, wr.root, req.getRequestURI())
                    .setArchiveFormat(getArchiveFormat(getAccess(req)))
                    .toSoyData(wr.id, wr.tw)));
  }

  private CanonicalTreeParser getOnlyChildSubtree(ObjectReader reader, ObjectId id, byte[] prefix)
      throws IOException {
    CanonicalTreeParser p = new CanonicalTreeParser(prefix, reader, id);
    if (p.eof() || p.getEntryFileMode() != FileMode.TREE) {
      return null;
    }
    p.next(1);
    return p.eof() ? p : null;
  }

  private void showFile(HttpServletRequest req, HttpServletResponse res, WalkResult wr)
      throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Map<String, ?> data = new BlobSoyData(wr.getObjectReader(), view).toSoyData(wr.path, wr.id);
    // TODO(sop): Allow caching files by SHA-1 when no S cookie is sent.
    renderHtml(
        req,
        res,
        "gitiles.pathDetail",
        ImmutableMap.of(
            "title", ViewFilter.getView(req).getPathPart(),
            "breadcrumbs", view.getBreadcrumbs(wr.hasSingleTree),
            "type", wr.type.toString(),
            "data", data));
  }

  private void showSymlink(HttpServletRequest req, HttpServletResponse res, WalkResult wr)
      throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Map<String, Object> data = Maps.newHashMap();

    ObjectLoader loader = wr.getObjectReader().open(wr.id, OBJ_BLOB);
    String target;
    try {
      target = RawParseUtils.decode(loader.getCachedBytes(TreeSoyData.MAX_SYMLINK_SIZE));
    } catch (LargeObjectException.OutOfMemory e) {
      throw e;
    } catch (LargeObjectException e) {
      data.put("sha", ObjectId.toString(wr.id));
      data.put("data", null);
      data.put("size", Long.toString(loader.getSize()));
      renderHtml(
          req,
          res,
          "gitiles.pathDetail",
          ImmutableMap.of(
              "title", ViewFilter.getView(req).getPathPart(),
              "breadcrumbs", view.getBreadcrumbs(wr.hasSingleTree),
              "type", FileType.REGULAR_FILE.toString(),
              "data", data));
      return;
    }

    String url =
        resolveTargetUrl(
            GitilesView.path().copyFrom(view).setPathPart(dirname(view.getPathPart())).build(),
            target);
    data.put("title", view.getPathPart());
    data.put("target", target);
    if (url != null) {
      data.put("targetUrl", url);
    }

    // TODO(sop): Allow caching files by SHA-1 when no S cookie is sent.
    renderHtml(
        req,
        res,
        "gitiles.pathDetail",
        ImmutableMap.of(
            "title", ViewFilter.getView(req).getPathPart(),
            "breadcrumbs", view.getBreadcrumbs(wr.hasSingleTree),
            "type", FileType.SYMLINK.toString(),
            "data", data));
  }

  private static String dirname(String path) {
    while (path.charAt(path.length() - 1) == '/') {
      path = path.substring(0, path.length() - 1);
    }
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash > 0) {
      return path.substring(0, lastSlash - 1);
    } else if (lastSlash == 0) {
      return "/";
    } else {
      return ".";
    }
  }

  private void showGitlink(HttpServletRequest req, HttpServletResponse res, WalkResult wr)
      throws IOException {
    GitilesView view = ViewFilter.getView(req);
    String modulesUrl;
    String remoteUrl = null;

    try (SubmoduleWalk sw =
        SubmoduleWalk.forPath(ServletUtils.getRepository(req), wr.root, view.getPathPart())) {
      modulesUrl = sw.getModulesUrl();
      if (modulesUrl != null && (modulesUrl.startsWith("./") || modulesUrl.startsWith("../"))) {
        String moduleRepo = PathUtil.simplifyPathUpToRoot(modulesUrl, view.getRepositoryName());
        if (moduleRepo != null) {
          modulesUrl = urls.getBaseGitUrl(req) + moduleRepo;
        }
      } else {
        remoteUrl = sw.getRemoteUrl();
      }
    } catch (ConfigInvalidException e) {
      throw new IOException(e);
    }

    Map<String, Object> data = Maps.newHashMap();
    data.put("sha", ObjectId.toString(wr.id));
    data.put("remoteUrl", remoteUrl != null ? remoteUrl : modulesUrl);

    // TODO(dborowitz): Guess when we can put commit SHAs in the URL.
    String httpUrl = resolveHttpUrl(remoteUrl);
    if (httpUrl != null) {
      data.put("httpUrl", httpUrl);
    }

    // TODO(sop): Allow caching links by SHA-1 when no S cookie is sent.
    renderHtml(
        req,
        res,
        "gitiles.pathDetail",
        ImmutableMap.of(
            "title", view.getPathPart(),
            "type", FileType.GITLINK.toString(),
            "data", data));
  }

  private static String resolveHttpUrl(String remoteUrl) {
    if (remoteUrl == null) {
      return null;
    }
    return VERBATIM_SUBMODULE_URL_PATTERN.matcher(remoteUrl).matches() ? remoteUrl : null;
  }
}
