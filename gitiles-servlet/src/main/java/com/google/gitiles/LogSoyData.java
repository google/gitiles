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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gitiles.CommitData.Field;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

public class LogSoyData {
  private static final ImmutableSet<Field> FIELDS = Sets.immutableEnumSet(Field.ABBREV_SHA,
      Field.SHA, Field.URL, Field.SHORT_MESSAGE, Field.MESSAGE, Field.AUTHOR, Field.COMMITTER,
      Field.BRANCHES, Field.TAGS);
  private static final ImmutableSet<Field> VERBOSE_FIELDS = Field.setOf(FIELDS, Field.DIFF_TREE);

  private final HttpServletRequest req;
  private final GitilesView view;
  private final boolean verbose;

  public LogSoyData(HttpServletRequest req, GitilesView view) {
    this(req, view, false);
  }

  public LogSoyData(HttpServletRequest req, GitilesView view, boolean verbose) {
    this.req = req;
    this.view = view;
    this.verbose = verbose;
  }

  public Map<String, Object> toSoyData(RevWalk walk, int limit, @Nullable String revision,
      @Nullable ObjectId start, DateFormatter df) throws IOException {
    return toSoyData(new Paginator(walk, limit, start), revision, df);
  }

  public Map<String, Object> toSoyData(Paginator paginator, @Nullable String revision,
      DateFormatter df) throws IOException {
    Map<String, Object> data = Maps.newHashMapWithExpectedSize(3);

    List<Map<String, Object>> entries = Lists.newArrayListWithCapacity(paginator.getLimit());
    for (RevCommit c : paginator) {
      Set<Field> fs = verbose ? VERBOSE_FIELDS : FIELDS;
      entries.add(new CommitSoyData().setRevWalk(paginator.getWalk()).toSoyData(req, c, fs, df));
    }

    data.put("entries", entries);
    ObjectId next = paginator.getNextStart();
    if (next != null) {
      data.put("nextUrl", copyAndCanonicalize(view, revision)
          .replaceParam(LogServlet.START_PARAM, next.name())
          .toUrl());
    }
    ObjectId prev = paginator.getPreviousStart();
    if (prev != null) {
      GitilesView.Builder prevView = copyAndCanonicalize(view, revision);
      if (!prevView.getRevision().getId().equals(prev)) {
        prevView.replaceParam(LogServlet.START_PARAM, prev.name());
      }
      data.put("previousUrl", prevView.toUrl());
    }
    return data;
  }

  private static GitilesView.Builder copyAndCanonicalize(GitilesView view, String revision) {
    // Canonicalize the view by using full SHAs.
    GitilesView.Builder copy = GitilesView.log().copyFrom(view);
    if (view.getRevision() != Revision.NULL) {
      copy.setRevision(view.getRevision());
    } else {
      copy.setRevision(Revision.named(revision));
    }
    if (view.getOldRevision() != Revision.NULL) {
      copy.setOldRevision(view.getOldRevision());
    }
    return copy;
  }
}
