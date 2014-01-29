// Copyright (C) 2014 Google Inc. All Rights Reserved.
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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves an HTML page with blame data for a commit. */
public class BlameServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  public BlameServlet(Config cfg, Renderer renderer) {
    super(cfg, renderer);
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    RevWalk rw = new RevWalk(repo);
    try {
      ObjectId blobId = resolveBlob(view, rw);
      if (blobId == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }

      String title = "Blame - " + view.getPathPart();
      Map<String, ?> blobData = new BlobSoyData(rw, view).toSoyData(view.getPathPart(), blobId);
      if (blobData.get("data") != null) {
        BlameResult blame = doBlame(repo, view);
        if (blame == null) {
          res.setStatus(SC_NOT_FOUND);
          return;
        }
        GitDateFormatter df = new GitDateFormatter(Format.DEFAULT);
        int lineCount = blame.getResultContents().size();
        blame.discardResultContents();
        renderHtml(req, res, "gitiles.blameDetail", ImmutableMap.of(
            "title", title,
            "breadcrumbs", view.getBreadcrumbs(),
            "data", blobData,
            "regions", toRegionData(view, rw.getObjectReader(), blame, lineCount, df)));
      } else {
        renderHtml(req, res, "gitiles.blameDetail", ImmutableMap.of(
            "title", title,
            "breadcrumbs", view.getBreadcrumbs(),
            "data", blobData));
      }
    } finally {
      rw.release();
    }
  }

  private static ObjectId resolveBlob(GitilesView view, RevWalk rw) throws IOException {
    try {
      TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), view.getPathPart(),
          rw.parseTree(view.getRevision().getId()));
      if ((tw.getRawMode(0) & FileMode.TYPE_FILE) == 0) {
        return null;
      }
      return tw.getObjectId(0);
    } catch (IncorrectObjectTypeException e) {
      return null;
    }
  }

  private static BlameResult doBlame(Repository repo, GitilesView view) throws IOException {
    BlameGenerator gen = new BlameGenerator(repo, view.getPathPart());
    BlameResult blame;
    try {
      // TODO: works on annotated tag?
      gen.push(null, view.getRevision().getId());
      blame = gen.computeBlameResult();
    } finally {
      gen.release();
    }
    return blame;
  }

  private List<Map<String, ?>> toRegionData(GitilesView view, ObjectReader reader,
      BlameResult blame, int lineCount, GitDateFormatter df) throws IOException {
    List<Region> regions = Lists.newArrayList();
    for (int i = 0; i < lineCount; i++) {
      if (regions.isEmpty() || !regions.get(regions.size() - 1).growFrom(blame, i)) {
        regions.add(new Region(blame, i));
      }
    }

    Map<ObjectId, String> abbrevShas = Maps.newHashMap();
    List<Map<String, ?>> result = Lists.newArrayListWithCapacity(regions.size());
    for (Region r : regions) {
      result.add(r.toSoyData(view, reader, abbrevShas, df));
    }
    return result;
  }

  private class Region {
    private final String sourcePath;
    private final RevCommit sourceCommit;
    private int count;

    private Region(BlameResult blame, int start) {
      this.sourcePath = blame.getSourcePath(start);
      this.sourceCommit = blame.getSourceCommit(start);
      this.count = 1;
    }

    private boolean growFrom(BlameResult blame, int i) {
      // Don't compare line numbers, so we collapse regions from the same source
      // but with deleted lines into one.
      if (Objects.equal(blame.getSourcePath(i), sourcePath)
          && Objects.equal(blame.getSourceCommit(i), sourceCommit)) {
        count++;
        return true;
      } else {
        return false;
      }
    }

    private Map<String, ?> toSoyData(GitilesView view, ObjectReader reader,
        Map<ObjectId, String> abbrevShas, GitDateFormatter df) throws IOException {
      if (sourceCommit == null) {
        // JGit bug may fail to blame some regions. We should fix this
        // upstream, but handle it for now.
        return ImmutableMap.of("count", count);
      }
      String abbrevSha = abbrevShas.get(sourceCommit);
      if (abbrevSha == null) {
        abbrevSha = reader.abbreviate(sourceCommit).name();
        abbrevShas.put(sourceCommit, abbrevSha);
      }
      return ImmutableMap.of(
          "abbrevSha", abbrevSha,
          "url", GitilesView.blame().copyFrom(view)
              .setRevision(sourceCommit.name())
              .setPathPart(sourcePath)
              .toUrl(),
          "author", CommitSoyData.toSoyData(sourceCommit.getAuthorIdent(), df),
          "count", count);
    }
  }
}
