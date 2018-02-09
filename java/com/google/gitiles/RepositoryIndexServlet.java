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
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gitiles.DateFormatter.Format;
import com.google.gitiles.doc.MarkdownConfig;
import com.google.gson.reflect.TypeToken;
import com.google.template.soy.data.SanitizedContent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

/** Serves the index page for a repository, if accessed directly by a browser. */
public class RepositoryIndexServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  static final int REF_LIMIT = 10;
  private static final int LOG_LIMIT = 20;
  private static final int LOG_WITH_README_LIMIT = 5;

  private final TimeCache timeCache;

  public RepositoryIndexServlet(
      GitilesAccess.Factory accessFactory, Renderer renderer, TimeCache timeCache) {
    super(renderer, accessFactory);
    this.timeCache = checkNotNull(timeCache, "timeCache");
  }

  @Override
  protected void doHead(HttpServletRequest req, HttpServletResponse res) throws IOException {
    // If the repository didn't exist a prior filter would have 404 replied.
    Optional<FormatType> format = getFormat(req);
    if (!format.isPresent()) {
      res.sendError(SC_BAD_REQUEST);
      return;
    }
    switch (format.get()) {
      case HTML:
      case JSON:
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType(format.get().getMimeType());
        break;
      case TEXT:
      case DEFAULT:
      default:
        res.sendError(SC_BAD_REQUEST);
        break;
    }
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);
    GitilesAccess access = getAccess(req);
    RepositoryDescription desc = access.getRepositoryDescription();

    try (RevWalk walk = new RevWalk(repo)) {
      Paginator paginator = null;
      Map<String, Object> data = Maps.newHashMapWithExpectedSize(7);
      List<Map<String, Object>> tags = RefServlet.getTagsSoyData(req, timeCache, walk, REF_LIMIT);
      ObjectId headId = repo.resolve(Constants.HEAD);
      if (headId != null) {
        RevObject head = walk.parseAny(headId);
        int limit = LOG_LIMIT;
        Map<String, Object> readme = renderReadme(req, walk, view, access.getConfig(), head);
        if (readme != null) {
          data.putAll(readme);
          limit = LOG_WITH_README_LIMIT;
        }
        // TODO(dborowitz): Handle non-commit or missing HEAD?
        if (head.getType() == Constants.OBJ_COMMIT) {
          walk.reset();
          walk.markStart((RevCommit) head);
          paginator = new Paginator(walk, limit, null);
        }
      }
      if (!data.containsKey("entries")) {
        data.put("entries", ImmutableList.of());
      }
      List<Map<String, Object>> branches = RefServlet.getBranchesSoyData(req, REF_LIMIT);

      data.put("cloneUrl", desc.cloneUrl);
      data.put("mirroredFromUrl", Strings.nullToEmpty(desc.mirroredFromUrl));
      data.put("description", Strings.nullToEmpty(desc.description));
      data.put("branches", trim(branches));
      if (branches.size() > REF_LIMIT) {
        data.put("moreBranchesUrl", GitilesView.refs().copyFrom(view).toUrl());
      }
      data.put("tags", trim(tags));
      data.put("hasLog", paginator != null);
      if (tags.size() > REF_LIMIT) {
        data.put("moreTagsUrl", GitilesView.refs().copyFrom(view).toUrl());
      }
      GitilesConfig.putVariant(getAccess(req).getConfig(), "logEntry", "logEntryVariant", data);

      if (paginator != null) {
        DateFormatter df = new DateFormatter(access, Format.DEFAULT);
        try (OutputStream out =
            startRenderStreamingHtml(req, res, "gitiles.repositoryIndex", data)) {
          Writer w = newWriter(out, res);
          new LogSoyData(req, access, "oneline")
              .renderStreaming(
                  paginator, "HEAD", renderer, w, df, LogSoyData.FooterBehavior.LOG_HEAD);
          w.flush();
        }
      } else {
        renderHtml(req, res, "gitiles.repositoryIndex", data);
      }
    }
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesAccess access = getAccess(req);
    RepositoryDescription desc = access.getRepositoryDescription();
    renderJson(req, res, desc, new TypeToken<RepositoryDescription>() {}.getType());
  }

  private static <T> List<T> trim(List<T> list) {
    return list.size() > REF_LIMIT ? list.subList(0, REF_LIMIT) : list;
  }

  private static Map<String, Object> renderReadme(
      HttpServletRequest req, RevWalk walk, GitilesView view, Config cfg, RevObject head)
      throws IOException {
    RevTree rootTree;
    try {
      rootTree = walk.parseTree(head);
    } catch (IncorrectObjectTypeException notTreeish) {
      return null;
    }

    ReadmeHelper readme =
        new ReadmeHelper(
            walk.getObjectReader(),
            GitilesView.path().copyFrom(view).setRevision(Revision.HEAD).setPathPart("/").build(),
            MarkdownConfig.get(cfg),
            rootTree,
            req.getRequestURI());
    readme.scanTree(rootTree);
    if (readme.isPresent()) {
      SanitizedContent html = readme.render();
      if (html != null) {
        return ImmutableMap.<String, Object>of("readmeHtml", html);
      }
    }
    return null;
  }
}
