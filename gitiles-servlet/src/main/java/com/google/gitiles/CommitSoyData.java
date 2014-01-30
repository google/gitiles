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
import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.data.restricted.NullData;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.http.server.ServletUtils;
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
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.RelativeDateFormatter;
import org.eclipse.jgit.util.io.NullOutputStream;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/** Soy data converter for git commits. */
public class CommitSoyData {
  /** Valid sets of keys to include in Soy data for commits. */
  public static enum KeySet {
    DETAIL("author", "committer", "sha", "tree", "treeUrl", "parents", "message", "logUrl",
        "archiveUrl", "archiveType"),
    DETAIL_DIFF_TREE(DETAIL, "diffTree"),
    SHORTLOG("abbrevSha", "url", "shortMessage", "author", "branches", "tags"),
    DEFAULT(DETAIL);

    private final Set<String> keys;

    private KeySet(String... keys) {
      this.keys = ImmutableSet.copyOf(keys);
    }

    private KeySet(KeySet other, String... keys) {
      this.keys = ImmutableSet.<String> builder().addAll(other.keys).add(keys).build();
    }

    private boolean contains(String key) {
      return keys.contains(key);
    }
  }

  private Linkifier linkifier;
  private Repository repo;
  private RevWalk walk;
  private GitilesView view;
  private Map<AnyObjectId, Set<Ref>> refsById;
  private ArchiveFormat archiveFormat;

  public CommitSoyData setLinkifier(Linkifier linkifier) {
    this.linkifier = checkNotNull(linkifier, "linkifier");
    return this;
  }

  public CommitSoyData setRevWalk(RevWalk walk) {
    this.walk = checkNotNull(walk, "walk");
    return this;
  }

  public CommitSoyData setArchiveFormat(ArchiveFormat archiveFormat) {
    this.archiveFormat = checkNotNull(archiveFormat, "archiveFormat");
    return this;
  }

  public Map<String, Object> toSoyData(HttpServletRequest req, RevCommit commit, KeySet ks,
      GitDateFormatter df) throws IOException {
    checkKeys(ks);
    checkNotNull(req, "request");
    repo = ServletUtils.getRepository(req);
    view = ViewFilter.getView(req);

    Map<String, Object> data = Maps.newHashMapWithExpectedSize(KeySet.DEFAULT.keys.size());
    if (ks.contains("author")) {
      data.put("author", toSoyData(commit.getAuthorIdent(), df));
    }
    if (ks.contains("committer")) {
      data.put("committer", toSoyData(commit.getCommitterIdent(), df));
    }
    if (ks.contains("sha")) {
      data.put("sha", ObjectId.toString(commit));
    }
    if (ks.contains("abbrevSha")) {
      ObjectReader reader = repo.getObjectDatabase().newReader();
      try {
        data.put("abbrevSha", reader.abbreviate(commit).name());
      } finally {
        reader.release();
      }
    }
    if (ks.contains("url")) {
      data.put("url", GitilesView.revision()
          .copyFrom(view)
          .setRevision(commit)
          .toUrl());
    }
    if (ks.contains("logUrl")) {
      data.put("logUrl", urlFromView(view, commit, GitilesView.log()));
    }
    if (ks.contains("archiveUrl")) {
      data.put("archiveUrl", urlFromView(view, commit,
          GitilesView.archive().setExtension(archiveFormat.getDefaultSuffix())));
    }
    if (ks.contains("archiveType")) {
      data.put("archiveType", archiveFormat.getShortName());
    }
    if (ks.contains("tree")) {
      data.put("tree", ObjectId.toString(commit.getTree()));
    }
    if (ks.contains("treeUrl")) {
      data.put("treeUrl", GitilesView.path().copyFrom(view).setPathPart("/").toUrl());
    }
    if (ks.contains("parents")) {
      data.put("parents", toSoyData(view, commit.getParents()));
    }
    if (ks.contains("shortMessage")) {
      data.put("shortMessage", commit.getShortMessage());
    }
    if (ks.contains("branches")) {
      data.put("branches", getRefsById(commit, Constants.R_HEADS));
    }
    if (ks.contains("tags")) {
      data.put("tags", getRefsById(commit, Constants.R_TAGS));
    }
    if (ks.contains("message")) {
      if (linkifier != null) {
        data.put("message", linkifier.linkify(req, commit.getFullMessage()));
      } else {
        data.put("message", commit.getFullMessage());
      }
    }
    if (ks.contains("diffTree")) {
      data.put("diffTree", computeDiffTree(commit));
    }
    checkState(ks.keys.size() == data.size(), "bad commit data keys: %s != %s", ks.keys,
        data.keySet());
    return ImmutableMap.copyOf(data);
  }

  public Map<String, Object> toSoyData(HttpServletRequest req, RevCommit commit,
      GitDateFormatter df) throws IOException {
    return toSoyData(req, commit, KeySet.DEFAULT, df);
  }

  private void checkKeys(KeySet ks) {
    checkState(!ks.contains("diffTree") || walk != null, "RevWalk required for diffTree");
    if (ks.contains("archiveUrl") || ks.contains("archiveType")) {
      checkState(archiveFormat != null, "archive format required");
    }
  }

  // TODO(dborowitz): Extract this.
  static Map<String, String> toSoyData(PersonIdent ident, GitDateFormatter df) {
    return ImmutableMap.of(
        "name", ident.getName(),
        "email", ident.getEmailAddress(),
        "time", df.formatDate(ident),
        // TODO(dborowitz): Switch from relative to absolute at some threshold.
        "relativeTime", RelativeDateFormatter.format(ident.getWhen()));
  }

  private List<Map<String, String>> toSoyData(GitilesView view, RevCommit[] parents) {
    List<Map<String, String>> result = Lists.newArrayListWithCapacity(parents.length);
    int i = 1;
    // TODO(dborowitz): Render something slightly different when we're actively
    // viewing a diff against one of the parents.
    for (RevCommit parent : parents) {
      String name = parent.name();
      GitilesView.Builder diff = GitilesView.diff().copyFrom(view).setPathPart("");
      String parentName;
      if (parents.length == 1) {
        parentName = view.getRevision().getName() + "^";
      } else {
        parentName = view.getRevision().getName() + "^" + (i++);
      }
      result.add(ImmutableMap.of(
          "sha", name,
          "url", GitilesView.revision()
              .copyFrom(view)
              .setRevision(parentName, parent)
              .toUrl(),
          "diffUrl", diff.setOldRevision(parentName, parent).toUrl()));
    }
    return result;
  }

  private AbstractTreeIterator getTreeIterator(RevWalk walk, RevCommit commit) throws IOException {
    CanonicalTreeParser p = new CanonicalTreeParser();
    p.reset(walk.getObjectReader(), walk.parseTree(walk.parseCommit(commit).getTree()));
    return p;
  }

  private Object computeDiffTree(RevCommit commit) throws IOException {
    AbstractTreeIterator oldTree;
    GitilesView.Builder diffUrl = GitilesView.diff().copyFrom(view)
        .setPathPart("");
    Revision oldRevision;
    switch (commit.getParentCount()) {
      case 0:
        oldTree = new EmptyTreeIterator();
        oldRevision = Revision.NULL;
        break;
      case 1:
        oldTree = getTreeIterator(walk, commit.getParent(0));
        oldRevision = Revision.peeled(view.getRevision().getName() + "^", commit.getParent(0));
        break;
      default:
        // TODO(dborowitz): handle merges
        return NullData.INSTANCE;
    }
    diffUrl.setOldRevision(oldRevision);
    AbstractTreeIterator newTree = getTreeIterator(walk, commit);

    DiffFormatter diff = new DiffFormatter(NullOutputStream.INSTANCE);
    try {
      diff.setRepository(repo);
      diff.setDetectRenames(true);

      List<Object> result = Lists.newArrayList();
      for (DiffEntry e : diff.scan(oldTree, newTree)) {
        Map<String, Object> entry = Maps.newHashMapWithExpectedSize(5);
        ChangeType type = e.getChangeType();
        if (type != DELETE) {
          entry.put("path", e.getNewPath());
          entry.put("url", GitilesView.path()
              .copyFrom(view)
              .setPathPart(e.getNewPath())
              .toUrl());
        } else {
          entry.put("path", e.getOldPath());
          entry.put("url", GitilesView.path()
              .copyFrom(view)
              .setRevision(oldRevision)
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
    } finally {
      diff.release();
    }
  }

  private static final Comparator<Map<String, String>> NAME_COMPARATOR =
      new Comparator<Map<String, String>>() {
        @Override
        public int compare(Map<String, String> o1, Map<String, String> o2) {
          return o1.get("name").compareTo(o2.get("name"));
        }
      };

  private List<Map<String, String>> getRefsById(ObjectId id, String prefix) {
    if (refsById == null) {
      refsById = repo.getAllRefsByPeeledObjectId();
    }
    Set<Ref> refs = refsById.get(id);
    if (refs == null) {
      return ImmutableList.of();
    }
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
    Collections.sort(result, NAME_COMPARATOR);
    return result;
  }

  private String urlFromView(GitilesView view, RevCommit commit, GitilesView.Builder builder) {
    Revision rev = view.getRevision();
    return builder.copyFrom(view)
        .setRevision(rev.getId().equals(commit) ? rev.getName() : commit.name(), commit)
        .setPathPart(null)
        .toUrl();
  }
}
