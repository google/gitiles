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

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Longs;
import com.google.gitiles.CommitData.Field;
import com.google.gitiles.DateFormatter.Format;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.AndRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serves an HTML page with a shortlog for commits and paths. */
public class LogServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(LogServlet.class);

  static final String LIMIT_PARAM = "n";
  static final String START_PARAM = "s";

  private static final String FOLLOW_PARAM = "follow";
  private static final String NAME_STATUS_PARAM = "name-status";
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

    Paginator paginator = null;
    try {
      GitilesAccess access = getAccess(req);
      paginator = newPaginator(repo, view, access);
      if (paginator == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }
      DateFormatter df = new DateFormatter(access, Format.DEFAULT);

      // Allow the user to select a logView variant with the "pretty" param.
      String pretty = Iterables.getFirst(view.getParameters().get(PRETTY_PARAM), "default");
      Map<String, Object> data = Maps.newHashMapWithExpectedSize(2);

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

      try (OutputStream out = startRenderStreamingHtml(req, res, "gitiles.logDetail", data)) {
        Writer w = newWriter(out, res);
        new LogSoyData(req, access, pretty)
            .renderStreaming(paginator, null, renderer, w, df, LogSoyData.FooterBehavior.NEXT);
        w.flush();
      }
    } catch (RevWalkException e) {
      log.warn("Error in rev walk", e);
      res.setStatus(SC_INTERNAL_SERVER_ERROR);
      return;
    } finally {
      if (paginator != null) {
        paginator.getWalk().close();
      }
    }
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    Repository repo = ServletUtils.getRepository(req);
    GitilesView view = getView(req, repo);

    Set<Field> fs = Sets.newEnumSet(CommitJsonData.DEFAULT_FIELDS, Field.class);
    String nameStatus = Iterables.getFirst(view.getParameters().get(NAME_STATUS_PARAM), null);
    if ("1".equals(nameStatus) || "".equals(nameStatus)) {
      fs.add(Field.DIFF_TREE);
    }

    Paginator paginator = null;
    try {
      GitilesAccess access = getAccess(req);
      paginator = newPaginator(repo, view, access);
      if (paginator == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }
      DateFormatter df = new DateFormatter(access, Format.DEFAULT);
      CommitJsonData.Log result = new CommitJsonData.Log();
      List<CommitJsonData.Commit> entries = Lists.newArrayListWithCapacity(paginator.getLimit());
      for (RevCommit c : paginator) {
        paginator.getWalk().parseBody(c);
        entries.add(
            new CommitJsonData().setRevWalk(paginator.getWalk()).toJsonData(req, c, fs, df));
      }
      result.log = entries;
      if (paginator.getPreviousStart() != null) {
        result.previous = paginator.getPreviousStart().name();
      }
      if (paginator.getNextStart() != null) {
        result.next = paginator.getNextStart().name();
      }
      renderJson(req, res, result, new TypeToken<CommitJsonData.Log>() {}.getType());
    } finally {
      if (paginator != null) {
        paginator.getWalk().close();
      }
    }
  }

  private static GitilesView getView(HttpServletRequest req, Repository repo) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    if (view.getRevision() != Revision.NULL) {
      return view;
    }
    Ref headRef = repo.exactRef(Constants.HEAD);
    if (headRef == null) {
      return null;
    }
    try (RevWalk walk = new RevWalk(repo)) {
      return GitilesView.log()
          .copyFrom(view)
          .setRevision(Revision.peel(Constants.HEAD, walk.parseAny(headRef.getObjectId()), walk))
          .build();
    }
  }

  private static class InvalidStartValueException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    InvalidStartValueException() {
      super();
    }
  }

  private static Optional<ObjectId> getStart(
      ListMultimap<String, String> params, ObjectReader reader)
      throws IOException, InvalidStartValueException {
    List<String> values = params.get(START_PARAM);
    switch (values.size()) {
      case 0:
        return Optional.empty();
      case 1:
        String id = values.get(0);
        if (!AbbreviatedObjectId.isId(id)) {
          throw new InvalidStartValueException();
        }
        Collection<ObjectId> ids = reader.resolve(AbbreviatedObjectId.fromString(id));
        if (ids.size() != 1) {
          throw new InvalidStartValueException();
        }
        return Optional.of(Iterables.getOnlyElement(ids));
      default:
        throw new InvalidStartValueException();
    }
  }

  private static RevWalk newWalk(Repository repo, GitilesView view, GitilesAccess access)
      throws MissingObjectException, IOException {
    RevWalk walk = new RevWalk(repo);
    try {
      walk.markStart(walk.parseCommit(view.getRevision().getId()));
      if (view.getOldRevision() != Revision.NULL) {
        walk.markUninteresting(walk.parseCommit(view.getOldRevision().getId()));
      }
    } catch (IncorrectObjectTypeException iote) {
      return null;
    }
    setTreeFilter(walk, view, access);
    setRevFilter(walk, view);
    if (isTrue(view, "topo-order")) {
      walk.sort(RevSort.TOPO, true);
    }
    if (isTrue(view, "reverse")) {
      walk.sort(RevSort.REVERSE, true);
    }
    return walk;
  }

  private static void setRevFilter(RevWalk walk, GitilesView view) {
    List<RevFilter> filters = new ArrayList<>(3);
    if (isTrue(view, "no-merges")) {
      filters.add(RevFilter.NO_MERGES);
    }

    String author = Iterables.getFirst(view.getParameters().get("author"), null);
    if (author != null) {
      filters.add(IdentRevFilter.author(author));
    }

    String committer = Iterables.getFirst(view.getParameters().get("committer"), null);
    if (committer != null) {
      filters.add(IdentRevFilter.committer(committer));
    }

    if (filters.size() > 1) {
      walk.setRevFilter(AndRevFilter.create(filters));
    } else if (filters.size() == 1) {
      walk.setRevFilter(filters.get(0));
    }
  }

  private static void setTreeFilter(RevWalk walk, GitilesView view, GitilesAccess access)
      throws IOException {
    if (Strings.isNullOrEmpty(view.getPathPart())) {
      return;
    }
    walk.setRewriteParents(false);
    String path = view.getPathPart();

    List<String> followParams = view.getParameters().get(FOLLOW_PARAM);
    boolean follow =
        !followParams.isEmpty()
            ? isTrue(followParams.get(0))
            : access.getConfig().getBoolean("log", null, "follow", true);
    if (follow) {
      walk.setTreeFilter(FollowFilter.create(path, access.getConfig().get(DiffConfig.KEY)));
    } else {
      walk.setTreeFilter(
          AndTreeFilter.create(PathFilterGroup.createFromStrings(path), TreeFilter.ANY_DIFF));
    }
  }

  private static boolean isTrue(GitilesView view, String param) {
    return isTrue(Iterables.getFirst(view.getParameters().get(param), null));
  }

  private static boolean isTrue(String v) {
    if (v == null) {
      return false;
    } else if (v.isEmpty()) {
      return true;
    }
    return Boolean.TRUE.equals(StringUtils.toBooleanOrNull(v));
  }

  private static Paginator newPaginator(Repository repo, GitilesView view, GitilesAccess access)
      throws IOException {
    if (view == null) {
      return null;
    }

    try (RevWalk walk = newWalk(repo, view, access)) {
      if (walk == null) {
        return null;
      }

      try {
        Optional<ObjectId> start = getStart(view.getParameters(), walk.getObjectReader());
        return new Paginator(walk, getLimit(view), start.orElse(null));
      } catch (InvalidStartValueException e) {
        return null;
      }
    }
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
