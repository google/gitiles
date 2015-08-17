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

import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gitiles.CommitData.DiffList;
import com.google.gitiles.CommitData.Field;
import com.google.template.soy.data.restricted.NullData;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RelativeDateFormatter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

/** Soy data converter for git commits. */
public class CommitSoyData {
  static final ImmutableSet<Field> DEFAULT_FIELDS = Sets.immutableEnumSet(Field.AUTHOR,
      Field.COMMITTER, Field.SHA, Field.TREE, Field.TREE_URL, Field.PARENTS, Field.MESSAGE,
      Field.LOG_URL, Field.ARCHIVE_URL, Field.ARCHIVE_TYPE);

  private static final ImmutableSet<Field> NESTED_FIELDS = Sets.immutableEnumSet(
      Field.PARENT_BLAME_URL);

  private Linkifier linkifier;
  private RevWalk walk;
  private CommitData.Builder cdb;
  private ArchiveFormat archiveFormat;

  CommitSoyData setLinkifier(@Nullable Linkifier linkifier) {
    this.linkifier = linkifier;
    return this;
  }

  CommitSoyData setRevWalk(@Nullable RevWalk walk) {
    this.walk = walk;
    return this;
  }

  CommitSoyData setArchiveFormat(@Nullable ArchiveFormat archiveFormat) {
    this.archiveFormat = archiveFormat;
    return this;
  }

  Map<String, Object> toSoyData(HttpServletRequest req, RevCommit c, Set<Field> fs,
      DateFormatter df) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    if (cdb == null) {
      cdb = new CommitData.Builder();
    }

    CommitData cd = cdb
        .setRevWalk(walk)
        .setArchiveFormat(archiveFormat)
        .build(req, c, fs);

    Map<String, Object> data = Maps.newHashMapWithExpectedSize(fs.size());
    if (cd.author != null) {
      data.put("author", toSoyData(cd.author, df));
    }
    if (cd.committer != null) {
      data.put("committer", toSoyData(cd.committer, df));
    }
    if (cd.sha != null) {
      data.put("sha", cd.sha.name());
    }
    if (cd.abbrev != null) {
      data.put("abbrevSha", cd.abbrev.name());
    }
    if (cd.url != null) {
      data.put("url", cd.url);
    }
    if (cd.logUrl != null) {
      data.put("logUrl", cd.logUrl);
    }
    if (cd.archiveUrl != null) {
      data.put("archiveUrl", cd.archiveUrl);
    }
    if (cd.archiveType != null) {
      data.put("archiveType", cd.archiveType.getShortName());
    }
    if (cd.tree != null) {
      data.put("tree", cd.tree.name());
    }
    if (cd.treeUrl != null) {
      data.put("treeUrl", cd.treeUrl);
    }
    if (cd.parents != null) {
      data.put("parents", toSoyData(view, fs, cd.parents));
    }
    if (cd.shortMessage != null) {
      data.put("shortMessage", cd.shortMessage);
    }
    if (cd.branches != null) {
      data.put("branches", toSoyData(view, cd.branches, Constants.R_HEADS));
    }
    if (cd.tags != null) {
      data.put("tags", toSoyData(view, cd.tags, Constants.R_TAGS));
    }
    if (cd.message != null) {
      if (linkifier != null) {
        data.put("message", linkifier.linkify(req, cd.message));
      } else {
        data.put("message", cd.message);
      }
    }
    if (cd.diffEntries != null) {
      data.put("diffTree", toSoyData(view, cd.diffEntries));
    }
    checkState(Sets.difference(fs, NESTED_FIELDS).size() == data.size(),
        "bad commit data fields: %s != %s", fs, data.keySet());
    return data;
  }

  Map<String, Object> toSoyData(HttpServletRequest req, RevCommit commit,
      DateFormatter df) throws IOException {
    return toSoyData(req, commit, DEFAULT_FIELDS, df);
  }

  // TODO(dborowitz): Extract this.
  public static Map<String, String> toSoyData(PersonIdent ident, DateFormatter df) {
    return ImmutableMap.of(
        "name", ident.getName(),
        "email", ident.getEmailAddress(),
        "time", df.format(ident),
        // TODO(dborowitz): Switch from relative to absolute at some threshold.
        "relativeTime", RelativeDateFormatter.format(ident.getWhen()));
  }

  private List<Map<String, String>> toSoyData(GitilesView view, Set<Field> fs,
      List<RevCommit> parents) {
    List<Map<String, String>> result = Lists.newArrayListWithCapacity(parents.size());
    int i = 1;
    // TODO(dborowitz): Render something slightly different when we're actively
    // viewing a diff against one of the parents.
    for (RevCommit parent : parents) {
      String name = parent.name();
      // Clear path on parent diff view, since this parent may not have a diff
      // for the path in question.
      GitilesView.Builder diff = GitilesView.diff().copyFrom(view).setPathPart("");
      String parentName;
      if (parents.size() == 1) {
        parentName = view.getRevision().getName() + "^";
      } else {
        parentName = view.getRevision().getName() + "^" + (i++);
      }
      diff.setOldRevision(parentName, parent);

      Map<String, String> e = Maps.newHashMapWithExpectedSize(4);
      e.put("sha", name);
      e.put("url", GitilesView.revision()
          .copyFrom(view)
          .setRevision(parentName, parent)
          .toUrl());
      e.put("diffUrl", diff.toUrl());
      if (fs.contains(Field.PARENT_BLAME_URL)) {
        // Assumes caller has ensured path is a file.
        e.put("blameUrl", GitilesView.blame()
            .copyFrom(view)
            .setRevision(Revision.peeled(parentName, parent))
            .toUrl());
      }
      result.add(e);
    }
    return result;
  }

  private static Object toSoyData(GitilesView view, DiffList dl) {
    if (dl.oldRevision == null) {
      return NullData.INSTANCE;
    }
    GitilesView.Builder diffUrl = GitilesView.diff()
        .copyFrom(view)
        .setOldRevision(dl.oldRevision)
        .setRevision(dl.revision)
        .setPathPart("");

    List<Object> result = Lists.newArrayListWithCapacity(dl.entries.size());
    for (DiffEntry e : dl.entries) {
      Map<String, Object> entry = Maps.newHashMapWithExpectedSize(5);
      ChangeType type = e.getChangeType();
      if (type != DELETE) {
        entry.put("path", e.getNewPath());
        entry.put("url", GitilesView.path()
            .copyFrom(view)
            .setRevision(dl.revision)
            .setPathPart(e.getNewPath())
            .toUrl());
      } else {
        entry.put("path", e.getOldPath());
        entry.put("url", GitilesView.path()
            .copyFrom(view)
            .setRevision(dl.oldRevision)
            .setPathPart(e.getOldPath())
            .toUrl());
      }
      entry.put("diffUrl", diffUrl.setAnchor("F" + result.size()).toUrl());
      entry.put("changeType", e.getChangeType().toString());
      if (type == COPY || type == RENAME) {
        entry.put("oldPath", e.getOldPath());
      }
      result.add(entry);
    }
    return result;
  }

  private static List<Map<String, String>> toSoyData(GitilesView view, List<Ref> refs,
      String prefix) {
    List<Map<String, String>> result = Lists.newArrayListWithCapacity(refs.size());
    for (Ref ref : refs) {
      if (ref.getName().startsWith(prefix)) {
        result.add(ImmutableMap.of(
          "name", ref.getName().substring(prefix.length()),
          "url", GitilesView.revision()
              .copyFrom(view)
              .setRevision(Revision.unpeeled(ref.getName(), ref.getObjectId()))
              .toUrl()));
      }
    }
    return result;
  }
}
