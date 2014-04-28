// Copyright (C) 2013 Google Inc. All Rights Reserved.
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
import com.google.common.collect.Sets;
import com.google.gitiles.CommitData.DiffList;
import com.google.gitiles.CommitData.Field;
import com.google.gitiles.DateFormatterBuilder.DateFormatter;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

class CommitJsonData {
  static final ImmutableSet<Field> DEFAULT_FIELDS = Sets.immutableEnumSet(
      Field.SHA, Field.PARENTS, Field.AUTHOR, Field.COMMITTER, Field.MESSAGE);

  static class Commit {
    String commit;
    List<String> parents;
    Ident author;
    Ident committer;
    String message;

    List<Diff> treeDiff;
  }

  static class Ident {
    String name;
    String email;
    String time;
  }

  /** @see DiffEntry */
  static class Diff {
    String type;
    String oldId;
    int oldMode;
    String oldPath;
    String newId;
    int newMode;
    String newPath;
    Integer score;
  }

  private RevWalk walk;

  CommitJsonData setRevWalk(@Nullable RevWalk walk) {
    this.walk = walk;
    return this;
  }

  Commit toJsonData(HttpServletRequest req, RevCommit c, DateFormatter df) throws IOException {
    return toJsonData(req, c, DEFAULT_FIELDS, df);
  }

  Commit toJsonData(HttpServletRequest req, RevCommit c, Set<Field> fs, DateFormatter df)
      throws IOException {
    CommitData cd = new CommitData.Builder()
        .setRevWalk(walk)
        .build(req, c, fs);

    Commit result = new Commit();
    if (cd.sha != null) {
      result.commit = cd.sha.name();
    }
    if (cd.parents != null) {
      result.parents = Lists.newArrayListWithCapacity(cd.parents.size());
      for (RevCommit parent : cd.parents) {
        result.parents.add(parent.name());
      }
    }
    if (cd.author != null) {
      result.author = toJsonData(cd.author, df);
    }
    if (cd.committer != null) {
      result.committer = toJsonData(cd.committer, df);
    }
    if (cd.message != null) {
      result.message = cd.message;
    }
    if (cd.diffEntries != null) {
      result.treeDiff = toJsonData(cd.diffEntries);
    }
    return result;
  }

  private static Ident toJsonData(PersonIdent ident, DateFormatter df) {
    Ident result = new Ident();
    result.name = ident.getName();
    result.email = ident.getEmailAddress();
    result.time = df.format(ident);
    return result;
  }

  private static List<Diff> toJsonData(DiffList dl) {
    List<Diff> result = Lists.newArrayListWithCapacity(dl.entries.size());
    for (DiffEntry de : dl.entries) {
      Diff d = new Diff();
      d.type = de.getChangeType().name().toLowerCase();
      d.oldId = de.getOldId().name();
      d.oldMode = de.getOldMode().getBits();
      d.oldPath = de.getOldPath();
      d.newId = de.getNewId().name();
      d.newMode = de.getNewMode().getBits();
      d.newPath = de.getNewPath();

      switch (de.getChangeType()) {
        case COPY:
        case RENAME:
          d.score = de.getScore();
          break;
        default:
          break;
      }

      result.add(d);
    }
    return result;
  }
}
