// Copyright 2013 Google Inc. All Rights Reserved.
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
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.Optional;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ArchiveServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  private final GitilesAccess.Factory accessFactory;

  public ArchiveServlet(GitilesAccess.Factory accessFactory) {
    super(null);
    this.accessFactory = checkNotNull(accessFactory, "accessFactory");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    GitilesView view = ViewFilter.getView(req);
    Revision rev = view.getRevision();
    Repository repo = ServletUtils.getRepository(req);

    ObjectId treeId = getTree(view, repo, rev);
    if (treeId.equals(ObjectId.zeroId())) {
      res.sendError(SC_NOT_FOUND);
      return;
    }

    Optional<ArchiveFormat> format = ArchiveFormat.byExtension(
        view.getExtension(), accessFactory.forRequest(req).getConfig());
    if (!format.isPresent()) {
      res.setStatus(SC_NOT_FOUND);
      return;
    }
    String filename = getFilename(view, rev, view.getExtension());
    setDownloadHeaders(res, filename, format.get().getMimeType());
    res.setStatus(SC_OK);

    try {
      new ArchiveCommand(repo)
          .setFormat(format.get().name())
          .setTree(treeId)
          .setOutputStream(res.getOutputStream())
          .call();
    } catch (GitAPIException e) {
      throw new IOException(e);
    }
  }

  private ObjectId getTree(GitilesView view, Repository repo, Revision rev) throws IOException {
    RevWalk rw = new RevWalk(repo);
    try {
      RevTree tree = rw.parseTree(rev.getId());
      if (view.getPathPart() == null) {
        return tree;
      }
      TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), view.getPathPart(), tree);
      if (tw.getFileMode(0) != FileMode.TREE) {
        return ObjectId.zeroId();
      }
      return tw.getObjectId(0);
    } catch (IncorrectObjectTypeException e) {
      return ObjectId.zeroId();
    } finally {
      rw.release();
    }
  }

  private String getFilename(GitilesView view, Revision rev, String ext) {
    StringBuilder sb = new StringBuilder()
        .append(Paths.basename(view.getRepositoryName()))
        .append('-')
        .append(rev.getName());
    if (view.getPathPart() != null) {
      sb.append('-')
          .append(view.getPathPart().replace('/', '-'));
    }
    return sb.append(ext)
        .toString();
  }
}
