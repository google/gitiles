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
import com.google.common.primitives.Bytes;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves an HTML page with detailed information about a path within a tree. */
// TODO(dborowitz): Handle non-UTF-8 names.
public class PathServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(PathServlet.class);

  /**
   * Submodule URLs where we know there is a web page if the user visits the
   * repository URL verbatim in a web browser.
   */
  private static final Pattern VERBATIM_SUBMODULE_URL_PATTERN =
      Pattern.compile("^(" + Joiner.on('|').join(
          "https?://[^.]+.googlesource.com/.*",
          "https?://[^.]+.googlecode.com/.*",
          "https?://code.google.com/p/.*",
          "https?://github.com/.*") + ")$", Pattern.CASE_INSENSITIVE);

  static final String AUTODIVE_PARAM = "autodive";
  static final String NO_AUTODIVE_VALUE = "0";

  static enum FileType {
    TREE(FileMode.TREE),
    SYMLINK(FileMode.SYMLINK),
    REGULAR_FILE(FileMode.REGULAR_FILE),
    EXECUTABLE_FILE(FileMode.EXECUTABLE_FILE),
    GITLINK(FileMode.GITLINK);

    private final FileMode mode;

    private FileType(FileMode mode) {
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

  public PathServlet(Renderer renderer) {
    super(renderer);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    RevWalk rw = new RevWalk(repo);
    try {
      RevObject obj = rw.peel(rw.parseAny(view.getRevision().getId()));
      RevTree root;

      switch (obj.getType()) {
        case OBJ_COMMIT:
          root = ((RevCommit) obj).getTree();
          break;
        case OBJ_TREE:
          root = (RevTree) obj;
          break;
        default:
          res.setStatus(SC_NOT_FOUND);
          return;
      }

      TreeWalk tw = new TreeWalk(rw.getObjectReader());
      tw.addTree(root);
      tw.setRecursive(false);
      FileType type;
      String path = view.getTreePath();
      List<Boolean> hasSingleTree;

      if (path.isEmpty()) {
        type = FileType.TREE;
        hasSingleTree = ImmutableList.<Boolean> of();
      } else {
        hasSingleTree = walkToPath(tw, path);
        if (hasSingleTree == null) {
          res.setStatus(SC_NOT_FOUND);
          return;
        }
        type = FileType.forEntry(tw);
      }

      switch (type) {
        case TREE:
          ObjectId treeId;
          if (path.isEmpty()) {
            treeId = root;
          } else {
            treeId = tw.getObjectId(0);
            tw.enterSubtree();
            tw.setRecursive(false);
          }
          showTree(req, res, rw, tw, treeId, hasSingleTree);
          break;
        case SYMLINK:
          showSymlink(req, res, rw, tw, hasSingleTree);
          break;
        case REGULAR_FILE:
        case EXECUTABLE_FILE:
          showFile(req, res, rw, tw, hasSingleTree);
          break;
        case GITLINK:
          showGitlink(req, res, rw, tw, root, hasSingleTree);
          break;
        default:
          log.error("Bad file type: {}", type);
          res.setStatus(SC_NOT_FOUND);
          break;
      }
    } catch (LargeObjectException e) {
      res.setStatus(SC_INTERNAL_SERVER_ERROR);
    } finally {
      rw.release();
    }
  }

  private static class AutoDiveFilter extends TreeFilter {
    /** @see GitilesView#getBreadcrumbs(List<Boolean>) */
    List<Boolean> hasSingleTree;

    private final byte[] pathRaw;
    private int count;
    private boolean done;

    AutoDiveFilter(String pathStr) {
      hasSingleTree = Lists.newArrayList();
      pathRaw = Constants.encode(pathStr);
    }

    @Override
    public boolean include(TreeWalk tw) throws MissingObjectException,
        IncorrectObjectTypeException, IOException {
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
      return Bytes.indexOf(pathRaw, (byte)'/') >= 0;
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

  private List<Boolean> walkToPath(TreeWalk tw, String pathString) throws IOException {
    AutoDiveFilter f = new AutoDiveFilter(pathString);
    tw.setFilter(f);
    while (tw.next()) {
      if (f.isDone(tw)) {
        return f.hasSingleTree;
      } else if (tw.isSubtree()) {
        tw.enterSubtree();
      }
    }
    return null;
  }

  private void showTree(HttpServletRequest req, HttpServletResponse res, RevWalk rw, TreeWalk tw,
      ObjectId id, List<Boolean> hasSingleTree) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    List<String> autodive = view.getParameters().get(AUTODIVE_PARAM);
    if (autodive.size() != 1 || !NO_AUTODIVE_VALUE.equals(autodive.get(0))) {
      byte[] path = Constants.encode(view.getTreePath());
      CanonicalTreeParser child = getOnlyChildSubtree(rw, id, path);
      if (child != null) {
        while (true) {
          path = new byte[child.getEntryPathLength()];
          System.arraycopy(child.getEntryPathBuffer(), 0, path, 0, child.getEntryPathLength());
          CanonicalTreeParser next = getOnlyChildSubtree(rw, child.getEntryObjectId(), path);
          if (next == null) {
            break;
          }
          child = next;
        }
        res.sendRedirect(GitilesView.path().copyFrom(view)
            .setTreePath(
                RawParseUtils.decode(child.getEntryPathBuffer(), 0, child.getEntryPathLength()))
            .toUrl());
        return;
      }
    }
    // TODO(sop): Allow caching trees by SHA-1 when no S cookie is sent.
    renderHtml(req, res, "gitiles.pathDetail", ImmutableMap.of(
        "title", !view.getTreePath().isEmpty() ? view.getTreePath() : "/",
        "breadcrumbs", view.getBreadcrumbs(hasSingleTree),
        "type", FileType.TREE.toString(),
        "data", new TreeSoyData(rw, view).toSoyData(id, tw)));
  }

  private CanonicalTreeParser getOnlyChildSubtree(RevWalk rw, ObjectId id, byte[] prefix)
      throws IOException {
    CanonicalTreeParser p = new CanonicalTreeParser(prefix, rw.getObjectReader(), id);
    if (p.eof() || p.getEntryFileMode() != FileMode.TREE) {
      return null;
    }
    p.next(1);
    return p.eof() ? p : null;
  }

  private void showFile(HttpServletRequest req, HttpServletResponse res, RevWalk rw, TreeWalk tw,
      List<Boolean> hasSingleTree) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    // TODO(sop): Allow caching files by SHA-1 when no S cookie is sent.
    renderHtml(req, res, "gitiles.pathDetail", ImmutableMap.of(
        "title", ViewFilter.getView(req).getTreePath(),
        "breadcrumbs", view.getBreadcrumbs(hasSingleTree),
        "type", FileType.forEntry(tw).toString(),
        "data", new BlobSoyData(rw, view).toSoyData(tw.getPathString(), tw.getObjectId(0))));
  }

  private void showSymlink(HttpServletRequest req, HttpServletResponse res, RevWalk rw,
      TreeWalk tw, List<Boolean> hasSingleTree) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    ObjectId id = tw.getObjectId(0);
    Map<String, Object> data = Maps.newHashMap();

    ObjectLoader loader = rw.getObjectReader().open(id, OBJ_BLOB);
    String target;
    try {
      target = RawParseUtils.decode(loader.getCachedBytes(TreeSoyData.MAX_SYMLINK_SIZE));
    } catch (LargeObjectException.OutOfMemory e) {
      throw e;
    } catch (LargeObjectException e) {
      data.put("sha", ObjectId.toString(id));
      data.put("data", null);
      data.put("size", Long.toString(loader.getSize()));
      renderHtml(req, res, "gitiles.pathDetail", ImmutableMap.of(
          "title", ViewFilter.getView(req).getTreePath(),
          "breadcrumbs", view.getBreadcrumbs(hasSingleTree),
          "type", FileType.REGULAR_FILE.toString(),
          "data", data));
      return;
    }

    String url = resolveTargetUrl(
        GitilesView.path()
            .copyFrom(view)
            .setTreePath(dirname(view.getTreePath()))
            .build(),
        target);
    data.put("title", view.getTreePath());
    data.put("target", target);
    if (url != null) {
      data.put("targetUrl", url);
    }

    // TODO(sop): Allow caching files by SHA-1 when no S cookie is sent.
    renderHtml(req, res, "gitiles.pathDetail", ImmutableMap.of(
        "title", ViewFilter.getView(req).getTreePath(),
        "breadcrumbs", view.getBreadcrumbs(hasSingleTree),
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

  private void showGitlink(HttpServletRequest req, HttpServletResponse res, RevWalk rw,
      TreeWalk tw, RevTree root, List<Boolean> hasSingleTree) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    SubmoduleWalk sw = SubmoduleWalk.forPath(ServletUtils.getRepository(req), root,
        view.getTreePath());

    String remoteUrl;
    try {
      remoteUrl = sw.getRemoteUrl();
    } catch (ConfigInvalidException e) {
      throw new IOException(e);
    } finally {
      sw.release();
    }

    Map<String, Object> data = Maps.newHashMap();
    data.put("sha", ObjectId.toString(tw.getObjectId(0)));
    data.put("remoteUrl", remoteUrl);

      // TODO(dborowitz): Guess when we can put commit SHAs in the URL.
      String httpUrl = resolveHttpUrl(remoteUrl);
      if (httpUrl != null) {
        data.put("httpUrl", httpUrl);
      }

      // TODO(sop): Allow caching links by SHA-1 when no S cookie is sent.
      renderHtml(req, res, "gitiles.pathDetail", ImmutableMap.of(
          "title", view.getTreePath(),
          "type", FileType.GITLINK.toString(),
          "data", data));
  }

  private static String resolveHttpUrl(String remoteUrl) {
    return VERBATIM_SUBMODULE_URL_PATTERN.matcher(remoteUrl).matches() ? remoteUrl : null;
  }
}
