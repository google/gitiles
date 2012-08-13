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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/** Object to parse revisions out of Gitiles paths. */
class RevisionParser {
  static final Splitter PATH_SPLITTER = Splitter.on('/');
  private static final Splitter OPERATOR_SPLITTER = Splitter.on(CharMatcher.anyOf("^~"));

  static class Result {
    private final Revision revision;
    private final Revision oldRevision;
    private final int pathStart;

    @VisibleForTesting
    Result(Revision revision) {
      this(revision, null, revision.getName().length());
    }

    @VisibleForTesting
    Result(Revision revision, Revision oldRevision, int pathStart) {
      this.revision = revision;
      this.oldRevision = oldRevision;
      this.pathStart = pathStart;
    }

    public Revision getRevision() {
      return revision;
    }

    public Revision getOldRevision() {
      return oldRevision;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Result) {
        Result r = (Result) o;
        return Objects.equal(revision, r.revision)
            && Objects.equal(oldRevision, r.oldRevision)
            && Objects.equal(pathStart, r.pathStart);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(revision, oldRevision, pathStart);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
          .omitNullValues()
          .add("revision", revision)
          .add("oldRevision", oldRevision)
          .add("pathStart", pathStart)
          .toString();
    }

    int getPathStart() {
      return pathStart;
    }
  }

  private final Repository repo;
  private final GitilesAccess access;
  private final VisibilityCache cache;

  RevisionParser(Repository repo, GitilesAccess access, VisibilityCache cache) {
    this.repo = checkNotNull(repo, "repo");
    this.access = checkNotNull(access, "access");
    this.cache = checkNotNull(cache, "cache");
  }

  Result parse(String path) throws IOException {
    RevWalk walk = new RevWalk(repo);
    try {
      Revision oldRevision = null;

      StringBuilder b = new StringBuilder();
      boolean first = true;
      for (String part : PATH_SPLITTER.split(path)) {
        if (part.isEmpty()) {
          return null; // No valid revision contains empty segments.
        }
        if (!first) {
          b.append('/');
        }

        if (oldRevision == null) {
          int dots = part.indexOf("..");
          int firstParent = part.indexOf("^!");
          if (dots == 0 || firstParent == 0) {
            return null;
          } else if (dots > 0) {
            b.append(part.substring(0, dots));
            String oldName = b.toString();
            if (!isValidRevision(oldName)) {
              return null;
            } else {
              ObjectId old = repo.resolve(oldName);
              if (old == null) {
                return null;
              }
              oldRevision = Revision.peel(oldName, old, walk);
            }
            part = part.substring(dots + 2);
            b = new StringBuilder();
          } else if (firstParent > 0) {
            if (firstParent != part.length() - 2) {
              return null;
            }
            b.append(part.substring(0, part.length() - 2));
            String name = b.toString();
            if (!isValidRevision(name)) {
              return null;
            }
            ObjectId id = repo.resolve(name);
            if (id == null) {
              return null;
            }
            RevCommit c;
            try {
              c = walk.parseCommit(id);
            } catch (IncorrectObjectTypeException e) {
              return null; // Not a commit, ^! is invalid.
            }
            if (c.getParentCount() > 0) {
              oldRevision = Revision.peeled(name + "^", c.getParent(0));
            } else {
              oldRevision = Revision.NULL;
            }
            Result result = new Result(Revision.peeled(name, c), oldRevision, name.length() + 2);
            return isVisible(walk, result) ? result : null;
          }
        }
        b.append(part);

        String name = b.toString();
        if (!isValidRevision(name)) {
          return null;
        }
        ObjectId id = repo.resolve(name);
        if (id != null) {
          int pathStart;
          if (oldRevision == null) {
            pathStart = name.length(); // foo
          } else {
            // foo..bar (foo may be empty)
            pathStart = oldRevision.getName().length() + 2 + name.length();
          }
          Result result = new Result(Revision.peel(name, id, walk), oldRevision, pathStart);
          return isVisible(walk, result) ? result : null;
        }
        first = false;
      }
      return null;
    } finally {
      walk.release();
    }
  }

  private static boolean isValidRevision(String revision) {
    // Disallow some uncommon but valid revision expressions that either we
    // don't support or we represent differently in our URLs.
    return revision.indexOf(':') < 0
        && revision.indexOf("^{") < 0
        && revision.indexOf('@') < 0;
  }

  private boolean isVisible(RevWalk walk, Result result) throws IOException {
    String maybeRef = OPERATOR_SPLITTER.split(result.getRevision().getName()).iterator().next();
    if (repo.getRef(maybeRef) != null) {
      // Name contains a visible ref; skip expensive reachability check.
      return true;
    }
    if (!cache.isVisible(repo, walk, access, result.getRevision().getId())) {
      return false;
    }
    if (result.getOldRevision() != null && result.getOldRevision() != Revision.NULL) {
      return cache.isVisible(repo, walk, access, result.getOldRevision().getId());
    } else {
      return true;
    }
  }
}
