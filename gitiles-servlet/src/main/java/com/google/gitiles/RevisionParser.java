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

import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/** Object to parse revisions out of Gitiles paths. */
class RevisionParser {
  private static final Splitter OPERATOR_SPLITTER = Splitter.on(CharMatcher.anyOf("^~"));

  static class Result {
    private final Revision revision;
    private final Revision oldRevision;
    private final String path;

    @VisibleForTesting
    Result(Revision revision) {
      this(revision, null, "");
    }

    @VisibleForTesting
    Result(Revision revision, Revision oldRevision, String path) {
      this.revision = revision;
      this.oldRevision = oldRevision;
      this.path = path;
    }

    public Revision getRevision() {
      return revision;
    }

    public Revision getOldRevision() {
      return oldRevision;
    }

    public String getPath() {
      return path;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Result) {
        Result r = (Result) o;
        return Objects.equal(revision, r.revision)
            && Objects.equal(oldRevision, r.oldRevision)
            && Objects.equal(path, r.path);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(revision, oldRevision, path);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
          .omitNullValues()
          .add("revision", revision.getName())
          .add("oldRevision", oldRevision != null ? oldRevision.getName() : null)
          .add("path", path)
          .toString();
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
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    RevWalk walk = new RevWalk(repo);
    try {
      Revision oldRevision = null;

      StringBuilder b = new StringBuilder();
      boolean first = true;
      for (String part : Paths.SPLITTER.split(path)) {
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
              RevObject old = resolve(oldName, walk);
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
            RevObject obj = resolve(name, walk);
            if (obj == null) {
              return null;
            }
            while (obj instanceof RevTag) {
              obj = ((RevTag) obj).getObject();
              walk.parseHeaders(obj);
            }
            if (!(obj instanceof RevCommit)) {
              return null; // Not a commit, ^! is invalid.
            }
            RevCommit c = (RevCommit) obj;
            if (c.getParentCount() > 0) {
              oldRevision = Revision.peeled(name + "^", c.getParent(0));
            } else {
              oldRevision = Revision.NULL;
            }
            Result result = new Result(Revision.peeled(name, c), oldRevision,
                path.substring(name.length() + 2));
            return isVisible(walk, result) ? result : null;
          }
        }
        b.append(part);

        String name = b.toString();
        if (!isValidRevision(name)) {
          return null;
        }
        RevObject obj = resolve(name, walk);
        if (obj != null) {
          int pathStart;
          if (oldRevision == null) {
            pathStart = name.length(); // foo
          } else {
            // foo..bar (foo may be empty)
            pathStart = oldRevision.getName().length() + 2 + name.length();
          }
          Result result = new Result(Revision.peel(name, obj, walk), oldRevision, path.substring(pathStart));
          return isVisible(walk, result) ? result : null;
        }
        first = false;
      }
      return null;
    } finally {
      walk.release();
    }
  }

  private RevObject resolve(String name, RevWalk walk) throws IOException {
    try {
      ObjectId id = repo.resolve(name);
      return id != null ? walk.parseAny(id) : null;
    } catch (AmbiguousObjectException e) {
      // TODO(dborowitz): Render a helpful disambiguation page.
      return null;
    } catch (RevisionSyntaxException e) {
      return null;
    } catch (MissingObjectException e) {
      return null;
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
    ObjectId id = result.getRevision().getId();
    if (!cache.isVisible(repo, walk, access, id)) {
      return false;
    }
    if (result.getOldRevision() != null && result.getOldRevision() != Revision.NULL) {
      return cache.isVisible(repo, walk, access, result.getOldRevision().getId(), id);
    } else {
      return true;
    }
  }
}
