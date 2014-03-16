// Copyright (C) 2014 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.NullOutputStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

/** Format-independent data about a single commit. */
class CommitData {
  static enum Field {
    ABBREV_SHA,
    ARCHIVE_TYPE,
    ARCHIVE_URL,
    AUTHOR,
    BRANCHES,
    COMMITTER,
    DIFF_TREE,
    LOG_URL,
    MESSAGE,
    PARENTS,
    SHA,
    SHORT_MESSAGE,
    TAGS,
    TREE,
    TREE_URL,
    URL;

    static ImmutableSet<Field> setOf(Iterable<Field> base, Field... fields) {
      return Sets.immutableEnumSet(Iterables.concat(base, Arrays.asList(fields)));
    }
  }

  static class DiffList {
    Revision oldRevision;
    List<DiffEntry> entries;
  }

  static class Builder {
    private RevWalk walk;
    private ArchiveFormat archiveFormat;
    private Map<AnyObjectId, Set<Ref>> refsById;

    Builder setRevWalk(@Nullable RevWalk walk) {
      this.walk = walk;
      return this;
    }

    Builder setArchiveFormat(@Nullable ArchiveFormat archiveFormat) {
      this.archiveFormat = archiveFormat;
      return this;
    }

    CommitData build(HttpServletRequest req, RevCommit c, Set<Field> fs)
        throws IOException {
      checkFields(fs);
      checkNotNull(req, "request");
      Repository repo = ServletUtils.getRepository(req);
      GitilesView view = ViewFilter.getView(req);

      CommitData result = new CommitData();

      if (fs.contains(Field.AUTHOR)) {
        result.author = c.getAuthorIdent();
      }
      if (fs.contains(Field.COMMITTER)) {
        result.committer = c.getCommitterIdent();
      }
      if (fs.contains(Field.SHA)) {
        result.sha = c.copy();
      }
      if (fs.contains(Field.ABBREV_SHA)) {
        ObjectReader reader = repo.getObjectDatabase().newReader();
        try {
          result.abbrev = reader.abbreviate(c);
        } finally {
          reader.release();
        }
      }
      if (fs.contains(Field.URL)) {
        result.url = GitilesView.revision()
            .copyFrom(view)
            .setRevision(c)
            .toUrl();
      }
      if (fs.contains(Field.LOG_URL)) {
        result.logUrl = urlFromView(view, c, GitilesView.log());
      }
      if (fs.contains(Field.ARCHIVE_URL)) {
        result.archiveUrl = urlFromView(view, c,
            GitilesView.archive().setExtension(archiveFormat.getDefaultSuffix()));
      }
      if (fs.contains(Field.ARCHIVE_TYPE)) {
        result.archiveType = archiveFormat;
      }
      if (fs.contains(Field.TREE)) {
        result.tree = c.getTree().copy();
      }
      if (fs.contains(Field.TREE_URL)) {
        result.treeUrl = GitilesView.path().copyFrom(view).setPathPart("/").toUrl();
      }
      if (fs.contains(Field.PARENTS)) {
        result.parents = Arrays.asList(c.getParents());
      }
      if (fs.contains(Field.SHORT_MESSAGE)) {
        result.shortMessage = c.getShortMessage();
      }
      if (fs.contains(Field.BRANCHES)) {
        result.branches = getRefsById(repo, c, Constants.R_HEADS);
      }
      if (fs.contains(Field.TAGS)) {
        result.tags = getRefsById(repo, c, Constants.R_TAGS);
      }
      if (fs.contains(Field.MESSAGE)) {
        result.message = c.getFullMessage();
      }
      if (fs.contains(Field.DIFF_TREE)) {
        result.diffEntries = computeDiffEntries(repo, view, c);
      }

      return result;
    }

    private void checkFields(Set<Field> fs) {
      checkState(!fs.contains(Field.DIFF_TREE) || walk != null, "RevWalk required for diffTree");
      if (fs.contains(Field.ARCHIVE_URL) || fs.contains(Field.ARCHIVE_TYPE)) {
        checkState(archiveFormat != null, "archive format required");
      }
    }

    private static String urlFromView(GitilesView view, RevCommit commit,
        GitilesView.Builder builder) {
      Revision rev = view.getRevision();
      return builder.copyFrom(view)
          .setRevision(rev.getId().equals(commit) ? rev.getName() : commit.name(), commit)
          .setPathPart(null)
          .toUrl();
    }

    private List<Ref> getRefsById(Repository repo, ObjectId id, final String prefix) {
      if (refsById == null) {
        refsById = repo.getAllRefsByPeeledObjectId();
      }
      return FluentIterable.from(Objects.firstNonNull(refsById.get(id), ImmutableSet.<Ref> of()))
        .filter(new Predicate<Ref>() {
          @Override
          public boolean apply(Ref ref) {
            return ref.getName().startsWith(prefix);
          }
        }).toSortedList(Ordering.natural().onResultOf(new Function<Ref, String>() {
          @Override
          public String apply(Ref ref) {
            return ref.getName();
          }
        }));
    }

    private AbstractTreeIterator getTreeIterator(RevCommit commit) throws IOException {
      CanonicalTreeParser p = new CanonicalTreeParser();
      p.reset(walk.getObjectReader(), walk.parseTree(walk.parseCommit(commit).getTree()));
      return p;
    }

    private DiffList computeDiffEntries(Repository repo, GitilesView view, RevCommit commit)
        throws IOException {
      DiffList result = new DiffList();
      AbstractTreeIterator oldTree;
      switch (commit.getParentCount()) {
        case 0:
          result.oldRevision = Revision.NULL;
          oldTree = new EmptyTreeIterator();
          break;
        case 1:
          result.oldRevision =
              Revision.peeled(view.getRevision().getName() + "^", commit.getParent(0));
          oldTree = getTreeIterator(commit.getParent(0));
          break;
        default:
          // TODO(dborowitz): handle merges
          return result;
      }
      AbstractTreeIterator newTree = getTreeIterator(commit);

      DiffFormatter diff = new DiffFormatter(NullOutputStream.INSTANCE);
      try {
        diff.setRepository(repo);
        diff.setDetectRenames(true);
        result.entries = diff.scan(oldTree, newTree);
        return result;
      } finally {
        diff.release();
      }
    }
  }

  ObjectId sha;
  PersonIdent author;
  PersonIdent committer;
  AbbreviatedObjectId abbrev;
  ObjectId tree;
  List<RevCommit> parents;
  String shortMessage;
  String message;

  List<Ref> branches;
  List<Ref> tags;
  DiffList diffEntries;

  String url;
  String logUrl;
  String treeUrl;
  String archiveUrl;
  ArchiveFormat archiveType;
}
