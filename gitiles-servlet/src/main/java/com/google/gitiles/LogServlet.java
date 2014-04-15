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
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.google.gitiles.DateFormatter.Format;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves an HTML page with a shortlog for commits and paths. */
public class LogServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(LogServlet.class);

  static final String LIMIT_PARAM = "n";
  static final String START_PARAM = "s";
  private static final String PRETTY_PARAM = "pretty";
  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 10000;

  private final Linkifier linkifier;

  public LogServlet(GitilesAccess.Factory accessFactory, Renderer renderer, Linkifier linkifier) {
    super(renderer, accessFactory);
    this.linkifier = checkNotNull(linkifier, "linkifier");
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    Repository repo = ServletUtils.getRepository(req);
    GitilesView view = getView(req, repo);
    Paginator paginator = newPaginator(repo, view);
    if (paginator == null) {
      res.setStatus(SC_NOT_FOUND);
      return;
    }

    try {
      GitilesAccess access = getAccess(req);
      Config config = access.getConfig();
      DateFormatter df = new DateFormatter(access, Format.DEFAULT);

      // Allow the user to select a logView variant with the "pretty" param.
      String pretty = Iterables.getFirst(view.getParameters().get(PRETTY_PARAM), "default");
      Map<String, Object> data = new LogSoyData(req, view,
          config.getBoolean("logFormat", pretty, "verbose", false)).toSoyData(paginator, null, df);
      String variant = config.getString("logFormat", pretty, "variant");
      data.put("logEntryPretty", pretty);
      data.put("logEntryVariant", Objects.firstNonNull(variant, pretty));

      if (!view.getRevision().nameIsId()) {
        List<Map<String, Object>> tags = Lists.newArrayListWithExpectedSize(1);
        for (RevObject o : RevisionServlet.listObjects(paginator.getWalk(), view.getRevision())) {
          if (o instanceof RevTag) {
            tags.add(new TagSoyData(linkifier, req).toSoyData((RevTag) o, df));
          }
        }
        if (!tags.isEmpty()) {
          data.put("tags", tags);
        }
      }

      String title = "Log - ";
      if (view.getOldRevision() != Revision.NULL) {
        title += view.getRevisionRange();
      } else {
        title += view.getRevision().getName();
      }

      data.put("title", title);

      renderHtml(req, res, "gitiles.logDetail", data);
    } catch (RevWalkException e) {
      log.warn("Error in rev walk", e);
      res.setStatus(SC_INTERNAL_SERVER_ERROR);
      return;
    } finally {
      paginator.getWalk().release();
    }
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    Repository repo = ServletUtils.getRepository(req);
    GitilesView view = getView(req, repo);
    Paginator paginator = newPaginator(repo, view);
    if (paginator == null) {
      res.setStatus(SC_NOT_FOUND);
      return;
    }

    try {
      DateFormatter df = new DateFormatter(getAccess(req), Format.DEFAULT);
      Map<String, Object> result = Maps.newLinkedHashMap();
      List<CommitJsonData.Commit> entries = Lists.newArrayListWithCapacity(paginator.getLimit());
      for (RevCommit c : paginator) {
        paginator.getWalk().parseBody(c);
        entries.add(new CommitJsonData().setRevWalk(paginator.getWalk()).toJsonData(req, c, df));
      }
      result.put("log", entries);
      if (paginator.getPreviousStart() != null) {
        result.put("previous", paginator.getPreviousStart().name());
      }
      if (paginator.getNextStart() != null) {
        result.put("next", paginator.getNextStart().name());
      }
      renderJson(req, res, result, new TypeToken<Map<String, Object>>() {}.getType());
    } finally {
      paginator.getWalk().release();
    }
  }

  private static GitilesView getView(HttpServletRequest req, Repository repo) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    if (view.getRevision() != Revision.NULL) {
      return view;
    }
    Ref headRef = repo.getRef(Constants.HEAD);
    if (headRef == null) {
      return null;
    }
    RevWalk walk = new RevWalk(repo);
    try {
      return GitilesView.log()
        .copyFrom(view)
        .setRevision(Revision.peel(Constants.HEAD, walk.parseAny(headRef.getObjectId()), walk))
        .build();
    } finally {
      walk.release();
    }
  }

  private static Optional<ObjectId> getStart(ListMultimap<String, String> params,
      ObjectReader reader) throws IOException {
    List<String> values = params.get(START_PARAM);
    switch (values.size()) {
      case 0:
        return Optional.absent();
      case 1:
        Collection<ObjectId> ids = reader.resolve(AbbreviatedObjectId.fromString(values.get(0)));
        if (ids.size() != 1) {
          return null;
        }
        return Optional.of(Iterables.getOnlyElement(ids));
      default:
        return null;
    }
  }

  private static RevWalk newWalk(Repository repo, GitilesView view)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    RevWalk walk = new RevWalk(repo);
    walk.markStart(walk.parseCommit(view.getRevision().getId()));
    if (view.getOldRevision() != Revision.NULL) {
      walk.markUninteresting(walk.parseCommit(view.getOldRevision().getId()));
    }
    if (!Strings.isNullOrEmpty(view.getPathPart())) {
      walk.setRewriteParents(false);
      walk.setTreeFilter(AndTreeFilter.create(
          PathFilterGroup.createFromStrings(view.getPathPart()),
          TreeFilter.ANY_DIFF));
    }
    return walk;
  }

  private static Paginator newPaginator(Repository repo, GitilesView view) throws IOException {
    if (view == null) {
      return null;
    }

    RevWalk walk = null;
    try {
      walk = newWalk(repo, view);
    } catch (IncorrectObjectTypeException e) {
      return null;
    }

    Optional<ObjectId> start;
    try {
      start = getStart(view.getParameters(), walk.getObjectReader());
    } catch (IOException e) {
      walk.release();
      throw e;
    }
    if (start == null) {
      return null;
    }

    return new Paginator(walk, getLimit(view), start.orNull());
  }

  private static int getLimit(GitilesView view) {
    List<String> values = view.getParameters().get(LIMIT_PARAM);
    if (values.isEmpty()) {
      return DEFAULT_LIMIT;
    }
    Long limit = Longs.tryParse(values.get(0));
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    return (int) Math.min(limit, MAX_LIMIT);
  }
}
