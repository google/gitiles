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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gitiles.CommitSoyData.KeySet;

/** Serves an HTML page with a shortlog for commits and paths. */
public class LogServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(LogServlet.class);

  static final String START_PARAM = "s";
  private static final int LIMIT = 100;

  private final Linkifier linkifier;

  public LogServlet(Renderer renderer, Linkifier linkifier) {
    super(renderer);
    this.linkifier = checkNotNull(linkifier, "linkifier");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);
    RevWalk walk = null;
    try {
      try {
        walk = newWalk(repo, view);
      } catch (IncorrectObjectTypeException e) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }

      Optional<ObjectId> start = getStart(view.getParameters(), walk.getObjectReader());
      if (start == null) {
        res.setStatus(SC_NOT_FOUND);
        return;
      }

      Map<String, Object> data = new LogSoyData(req, repo, view)
        .toSoyData(walk, LIMIT, null, start.orNull());

      if (!view.getRevision().nameIsId()) {
        List<Map<String, Object>> tags = Lists.newArrayListWithExpectedSize(1);
        for (RevObject o : RevisionServlet.listObjects(walk, view.getRevision())) {
          if (o instanceof RevTag) {
            tags.add(new TagSoyData(linkifier, req).toSoyData((RevTag) o));
          }
        }
        if (!tags.isEmpty()) {
          data.put("tags", tags);
        }
      }

      Paginator paginator = new Paginator(walk, LIMIT, start.orNull());
      Map<AnyObjectId, Set<Ref>> refsById = repo.getAllRefsByPeeledObjectId();
      List<Map<String, Object>> entries = Lists.newArrayListWithCapacity(LIMIT);
      for (RevCommit c : paginator) {
        entries.add(new CommitSoyData(null, req, repo, walk, view, refsById)
            .toSoyData(c, KeySet.SHORTLOG));
      }

      String title = "Log - ";
      if (view.getOldRevision() != Revision.NULL) {
        title += view.getRevisionRange();
      } else {
        title += view.getRevision().getName();
      }

      data.put("title", title);

      render(req, res, "gitiles.logDetail", data);
    } catch (RevWalkException e) {
      log.warn("Error in rev walk", e);
      res.setStatus(SC_INTERNAL_SERVER_ERROR);
      return;
    } finally {
      if (walk != null) {
        walk.release();
      }
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
    if (!Strings.isNullOrEmpty(view.getTreePath())) {
      walk.setTreeFilter(FollowFilter.create(view.getTreePath()));
    }
    return walk;
  }
}
