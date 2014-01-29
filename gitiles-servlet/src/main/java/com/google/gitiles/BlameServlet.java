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

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gitiles.BlameCache.Region;

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

  private final BlameCache cache;

  public BlameServlet(Config cfg, Renderer renderer, BlameCache cache) {
    super(cfg, renderer);
    this.cache = checkNotNull(cache, "cache");
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    RevWalk rw = new RevWalk(repo);
    try {
      RevCommit commit = rw.parseCommit(view.getRevision().getId());
      ObjectId blobId = resolveBlob(view, rw, commit);
      if (blobId == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }

      String title = "Blame - " + view.getPathPart();
      Map<String, ?> blobData = new BlobSoyData(rw, view).toSoyData(view.getPathPart(), blobId);
      if (blobData.get("data") != null) {
        List<Region> regions = cache.get(repo, commit, view.getPathPart());
        if (regions.isEmpty()) {
          res.setStatus(SC_NOT_FOUND);
          return;
        }
        GitDateFormatter df = new GitDateFormatter(Format.DEFAULT);
        renderHtml(req, res, "gitiles.blameDetail", ImmutableMap.of(
            "title", title,
            "breadcrumbs", view.getBreadcrumbs(),
            "data", blobData,
            "regions", toSoyData(view, rw.getObjectReader(), regions, df)));
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

  private static ObjectId resolveBlob(GitilesView view, RevWalk rw, RevCommit commit)
      throws IOException {
    try {
      TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), view.getPathPart(), commit.getTree());
      if ((tw.getRawMode(0) & FileMode.TYPE_FILE) == 0) {
        return null;
      }
      return tw.getObjectId(0);
    } catch (IncorrectObjectTypeException e) {
      return null;
    }
  }

  private static List<Map<String, ?>> toSoyData(GitilesView view, ObjectReader reader,
      List<Region> regions, GitDateFormatter df) throws IOException {
    Map<ObjectId, String> abbrevShas = Maps.newHashMap();
    List<Map<String, ?>> result = Lists.newArrayListWithCapacity(regions.size());
    for (BlameCache.Region r : regions) {
      if (r.getSourceCommit() == null) {
        // JGit bug may fail to blame some regions. We should fix this
        // upstream, but handle it for now.
        result.add(ImmutableMap.of("count", r.getCount()));
      } else {
        String abbrevSha = abbrevShas.get(r.getSourceCommit());
        if (abbrevSha == null) {
          abbrevSha = reader.abbreviate(r.getSourceCommit()).name();
          abbrevShas.put(r.getSourceCommit(), abbrevSha);
        }
        result.add(ImmutableMap.of(
            "abbrevSha", abbrevSha,
            "url", GitilesView.blame().copyFrom(view)
                .setRevision(r.getSourceCommit().name())
                .setPathPart(r.getSourcePath())
                .toUrl(),
            "author", CommitSoyData.toSoyData(r.getSourceAuthor(), df),
            "count", r.getCount()));
      }
    }
    return result;
  }
}
