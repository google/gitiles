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
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
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

/** Format-independent data about a single commit. */
class CommitData {
  enum Field {
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
    PARENT_BLAME_URL,
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
    Revision revision;
    Revision oldRevision;
    List<DiffEntry> entries;
  }

  static class Builder {
    private ArchiveFormat archiveFormat;
    private Map<AnyObjectId, Set<Ref>> refsById;

    Builder setArchiveFormat(@Nullable ArchiveFormat archiveFormat) {
      this.archiveFormat = archiveFormat;
      return this;
    }

    CommitData build(HttpServletRequest req, RevWalk walk, RevCommit c, Set<Field> fs)
        throws IOException {
      checkFields(fs);
      checkNotNull(req, "request");
      checkNotNull(walk, "walk");
      Repository repo = ServletUtils.getRepository(req);
      GitilesView view = ViewFilter.getView(req);

      CommitData result = new CommitData();

      if (fs.contains(Field.AUTHOR)) {
        walk.parseBody(c);
        result.author = c.getAuthorIdent();
      }
      if (fs.contains(Field.COMMITTER)) {
        walk.parseBody(c);
        result.committer = c.getCommitterIdent();
      }
      if (fs.contains(Field.SHA)) {
        result.sha = c.copy();
      }
      if (fs.contains(Field.ABBREV_SHA)) {
        try (ObjectReader reader = repo.getObjectDatabase().newReader()) {
          result.abbrev = reader.abbreviate(c);
        }
      }
      if (fs.contains(Field.URL)) {
        result.url = GitilesView.revision().copyFrom(view).setRevision(c).toUrl();
      }
      if (fs.contains(Field.LOG_URL)) {
        result.logUrl = urlFromView(view, c, GitilesView.log());
      }
      if (fs.contains(Field.ARCHIVE_URL)) {
        result.archiveUrl =
            urlFromView(
                view, c, GitilesView.archive().setExtension(archiveFormat.getDefaultSuffix()));
      }
      if (fs.contains(Field.ARCHIVE_TYPE)) {
        result.archiveType = archiveFormat;
      }
      if (fs.contains(Field.TREE)) {
        result.tree = c.getTree().copy();
      }
      if (fs.contains(Field.TREE_URL)) {
        // Tree always implies the root tree.
        result.treeUrl = GitilesView.path().copyFrom(view).setPathPart("/").toUrl();
      }
      if (fs.contains(Field.PARENTS)) {
        result.parents = Arrays.asList(c.getParents());
      }
      if (fs.contains(Field.BRANCHES)) {
        result.branches = getRefsById(repo, c, Constants.R_HEADS);
      }
      if (fs.contains(Field.TAGS)) {
        result.tags = getRefsById(repo, c, Constants.R_TAGS);
      }
      if (fs.contains(Field.MESSAGE)) {
        walk.parseBody(c);
        result.message = c.getFullMessage();
      }
      if (fs.contains(Field.SHORT_MESSAGE)) {
        walk.parseBody(c);
        String msg = c.getShortMessage();
        if (msg.length() > 80) {
          String ft = result.message;
          if (ft == null) {
            ft = c.getFullMessage();
          }
          int lf = ft.indexOf('\n');
          if (lf > 0) {
            msg = ft.substring(0, lf);
          }
        }
        result.shortMessage = msg;
      }
      if (fs.contains(Field.DIFF_TREE)) {
        result.diffEntries = computeDiffEntries(repo, view, walk, c);
      }

      return result;
    }

    private void checkFields(Set<Field> fs) {
      if (fs.contains(Field.ARCHIVE_URL) || fs.contains(Field.ARCHIVE_TYPE)) {
        checkState(archiveFormat != null, "archive format required");
      }
    }

    private static String urlFromView(
        GitilesView view, RevCommit commit, GitilesView.Builder builder) {
      Revision rev = view.getRevision();
      return builder
          .copyFrom(view)
          .setOldRevision(Revision.NULL)
          .setRevision(rev.getId().equals(commit) ? rev.getName() : commit.name(), commit)
          .setPathPart(view.getPathPart())
          .toUrl();
    }

    private List<Ref> getRefsById(Repository repo, ObjectId id, String prefix) {
      if (refsById == null) {
        refsById = repo.getAllRefsByPeeledObjectId();
      }
      Set<Ref> refs = refsById.get(id);
      if (refs == null) {
        return ImmutableList.of();
      }
      return refs.stream()
          .filter(r -> r.getName().startsWith(prefix))
          .sorted(comparing(Ref::getName))
          .collect(toList());
    }

    private AbstractTreeIterator getTreeIterator(RevWalk walk, RevCommit commit)
        throws IOException {
      CanonicalTreeParser p = new CanonicalTreeParser();
      p.reset(walk.getObjectReader(), walk.parseTree(walk.parseCommit(commit).getTree()));
      return p;
    }

    private DiffList computeDiffEntries(
        Repository repo, GitilesView view, RevWalk walk, RevCommit commit) throws IOException {
      DiffList result = new DiffList();
      result.revision =
          view.getRevision().matches(commit)
              ? view.getRevision()
              : Revision.peeled(commit.name(), commit);

      AbstractTreeIterator oldTree;
      switch (commit.getParentCount()) {
        case 0:
          result.oldRevision = Revision.NULL;
          oldTree = new EmptyTreeIterator();
          break;
        case 1:
          result.oldRevision =
              Revision.peeled(result.revision.getName() + "^", commit.getParent(0));
          oldTree = getTreeIterator(walk, commit.getParent(0));
          break;
        default:
          // TODO(dborowitz): handle merges
          return result;
      }
      AbstractTreeIterator newTree = getTreeIterator(walk, commit);

      try (DiffFormatter diff = new DiffFormatter(NullOutputStream.INSTANCE)) {
        diff.setRepository(repo);
        diff.setDetectRenames(true);
        result.entries = diff.scan(oldTree, newTree);
        return result;
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
