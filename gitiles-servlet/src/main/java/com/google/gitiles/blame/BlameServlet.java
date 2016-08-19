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

package com.google.gitiles.blame;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gitiles.BaseServlet;
import com.google.gitiles.BlobSoyData;
import com.google.gitiles.CommitSoyData;
import com.google.gitiles.DateFormatter;
import com.google.gitiles.DateFormatter.Format;
import com.google.gitiles.GitilesAccess;
import com.google.gitiles.GitilesView;
import com.google.gitiles.Renderer;
import com.google.gitiles.ViewFilter;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serves an HTML page with blame data for a commit. */
public class BlameServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(BlameServlet.class);

  private final BlameCache cache;

  public BlameServlet(GitilesAccess.Factory accessFactory, Renderer renderer, BlameCache cache) {
    super(renderer, accessFactory);
    this.cache = checkNotNull(cache, "cache");
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    try (RevWalk rw = new RevWalk(repo)) {
      GitilesAccess access = getAccess(req);
      RegionResult result = getRegions(view, access, repo, rw, res);
      if (result == null) {
        return;
      }

      String title = "Blame - " + view.getPathPart();
      Map<String, ?> blobData =
          new BlobSoyData(rw.getObjectReader(), view).toSoyData(view.getPathPart(), result.blobId);
      if (blobData.get("lines") != null) {
        DateFormatter df = new DateFormatter(access, Format.ISO);
        renderHtml(
            req,
            res,
            "gitiles.blameDetail",
            ImmutableMap.of(
                "title",
                title,
                "breadcrumbs",
                view.getBreadcrumbs(),
                "data",
                blobData,
                "regions",
                toSoyData(view, rw.getObjectReader(), result.regions, df)));
      } else {
        renderHtml(
            req,
            res,
            "gitiles.blameDetail",
            ImmutableMap.of(
                "title", title,
                "breadcrumbs", view.getBreadcrumbs(),
                "data", blobData));
      }
    }
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    try (RevWalk rw = new RevWalk(repo)) {
      RegionResult result = getRegions(view, getAccess(req), repo, rw, res);
      if (result == null) {
        return;
      }
      // Output from BlameCache is 0-based for lines. We convert to 1-based for
      // JSON output later (in RegionAdapter); here we're just filling in the
      // transient fields.
      int start = 0;
      for (Region r : result.regions) {
        r.setStart(start);
        start += r.getCount();
      }
      renderJson(
          req,
          res,
          ImmutableMap.of("regions", result.regions),
          new TypeToken<Map<String, List<Region>>>() {}.getType());
    }
  }

  @Override
  protected GsonBuilder newGsonBuilder(HttpServletRequest req) throws IOException {
    return super.newGsonBuilder(req)
        .registerTypeAdapter(
            Region.class, new RegionAdapter(new DateFormatter(getAccess(req), Format.ISO)));
  }

  private static class RegionResult {
    private final List<Region> regions;
    private final ObjectId blobId;

    private RegionResult(List<Region> regions, ObjectId blobId) {
      this.regions = regions;
      this.blobId = blobId;
    }
  }

  private RegionResult getRegions(
      GitilesView view, GitilesAccess access, Repository repo, RevWalk rw, HttpServletResponse res)
      throws IOException {
    RevCommit currCommit = rw.parseCommit(view.getRevision().getId());
    ObjectId currCommitBlobId = resolveBlob(view, rw, currCommit);
    if (currCommitBlobId == null) {
      res.setStatus(SC_NOT_FOUND);
      return null;
    }

    ObjectId lastCommit = cache.findLastCommit(repo, currCommit, view.getPathPart());
    ObjectId lastCommitBlobId = resolveBlob(view, rw, lastCommit);

    if (!Objects.equals(currCommitBlobId, lastCommitBlobId)) {
      log.warn(
          String.format(
              "Blob %s in last modified commit %s for repo %s starting from %s"
                  + " does not match original blob %s",
              ObjectId.toString(lastCommitBlobId),
              ObjectId.toString(lastCommit),
              access.getRepositoryName(),
              ObjectId.toString(currCommit),
              ObjectId.toString(currCommitBlobId)));
      lastCommitBlobId = currCommitBlobId;
      lastCommit = currCommit;
    }

    List<Region> regions = cache.get(repo, lastCommit, view.getPathPart());
    if (regions.isEmpty()) {
      res.setStatus(SC_NOT_FOUND);
      return null;
    }
    return new RegionResult(regions, lastCommitBlobId);
  }

  private static ObjectId resolveBlob(GitilesView view, RevWalk rw, ObjectId commitId)
      throws IOException {
    try {
      if (commitId == null || Strings.isNullOrEmpty(view.getPathPart())) {
        return null;
      }
      RevTree tree = rw.parseTree(commitId);
      TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), view.getPathPart(), tree);
      if (tw == null || (tw.getRawMode(0) & FileMode.TYPE_MASK) != FileMode.TYPE_FILE) {
        return null;
      }
      return tw.getObjectId(0);
    } catch (IncorrectObjectTypeException e) {
      return null;
    }
  }

  private static final ImmutableList<String> CLASSES =
      ImmutableList.of("Blame-region--bg1", "Blame-region--bg2");
  private static final ImmutableList<SoyMapData> NULLS;

  static {
    ImmutableList.Builder<SoyMapData> nulls = ImmutableList.builder();
    for (String clazz : CLASSES) {
      nulls.add(new SoyMapData("class", clazz));
    }
    NULLS = nulls.build();
  }

  private static SoyListData toSoyData(
      GitilesView view, ObjectReader reader, List<Region> regions, DateFormatter df)
      throws IOException {
    Map<ObjectId, String> abbrevShas = Maps.newHashMap();
    SoyListData result = new SoyListData();

    for (int i = 0; i < regions.size(); i++) {
      Region r = regions.get(i);
      int c = i % CLASSES.size();
      if (r.getSourceCommit() == null) {
        // JGit bug may fail to blame some regions. We should fix this
        // upstream, but handle it for now.
        result.add(NULLS.get(c));
      } else {
        String abbrevSha = abbrevShas.get(r.getSourceCommit());
        if (abbrevSha == null) {
          abbrevSha = reader.abbreviate(r.getSourceCommit()).name();
          abbrevShas.put(r.getSourceCommit(), abbrevSha);
        }
        Map<String, Object> e = Maps.newHashMapWithExpectedSize(6);
        e.put("abbrevSha", abbrevSha);
        String blameParent = "";
        String blameText = "blame";
        if (view.getRevision().getName().equals(r.getSourceCommit().name())) {
          blameParent = "^";
          blameText = "blame^";
        }
        e.put(
            "blameUrl",
            GitilesView.blame()
                .copyFrom(view)
                .setRevision(r.getSourceCommit().name() + blameParent)
                .setPathPart(r.getSourcePath())
                .toUrl());
        e.put("blameText", blameText);
        e.put(
            "commitUrl",
            GitilesView.revision().copyFrom(view).setRevision(r.getSourceCommit().name()).toUrl());
        e.put(
            "diffUrl",
            GitilesView.diff()
                .copyFrom(view)
                .setRevision(r.getSourceCommit().name())
                .setPathPart(r.getSourcePath())
                .toUrl());
        e.put("author", CommitSoyData.toSoyData(r.getSourceAuthor(), df));
        e.put("class", CLASSES.get(c));
        result.add(e);
      }
      // Pad the list with null regions so we can iterate in parallel in the
      // template. We can't do this by maintaining an index variable into the
      // regions list because Soy {let} is an unmodifiable alias scoped to a
      // single block.
      for (int j = 0; j < r.getCount() - 1; j++) {
        result.add(NULLS.get(c));
      }
    }
    return result;
  }
}
