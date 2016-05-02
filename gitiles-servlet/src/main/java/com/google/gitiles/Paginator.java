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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RenameCallback;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Wrapper around {@link RevWalk} that paginates for Gitiles.
 *
 * A single page of a shortlog is defined by a revision range, such as "master"
 * or "master..next", a page size, and a start commit, such as "c0ffee". The
 * distance between the first commit in the walk ("next") and the first commit
 * in the page may be arbitrarily long, but in order to present the commit list
 * in a stable way, we must always start from the first commit in the walk. This
 * is because there may be arbitrary merge commits between "c0ffee" and "next"
 * that effectively insert arbitrary commits into the history starting from
 * "c0ffee".
 */
class Paginator implements Iterable<RevCommit> {
  private static class RenameWatcher extends RenameCallback {
    private DiffEntry entry;

    @Override
    public void renamed(DiffEntry entry) {
      this.entry = entry;
    }

    private DiffEntry getAndClear() {
      DiffEntry e = entry;
      entry = null;
      return e;
    }
  }

  private final RevWalk walk;
  private final int limit;
  private final ObjectId prevStart;
  private final RenameWatcher renameWatcher;

  private RevCommit first;
  private boolean done;
  private int n;
  private ObjectId nextStart;
  private Map<ObjectId, DiffEntry> renamed;

  /**
   * Construct a paginator and walk eagerly to the first returned commit.
   *
   * @param walk revision walk; must be fully initialized before calling.
   * @param limit page size.
   * @param start commit at which to start the walk, or null to start at the
   *     beginning.
   */
  Paginator(RevWalk walk, int limit, @Nullable ObjectId start)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    this.walk = checkNotNull(walk, "walk");
    checkArgument(limit > 0, "limit must be positive: %s", limit);
    this.limit = limit;

    TreeFilter filter = walk.getTreeFilter();
    if (filter instanceof FollowFilter) {
      renameWatcher = new RenameWatcher();
      ((FollowFilter) filter).setRenameCallback(renameWatcher);
    } else {
      renameWatcher = null;
    }

    Deque<ObjectId> prevBuffer = new ArrayDeque<>(start != null ? limit : 0);
    while (true) {
      RevCommit commit = nextWithRename();
      if (commit == null) {
        done = true;
        break;
      }
      if (start == null || start.equals(commit)) {
        first = commit;
        break;
      }
      if (prevBuffer.size() == limit) {
        prevBuffer.remove();
      }
      prevBuffer.add(commit);
    }
    prevStart = prevBuffer.pollFirst();
  }

  /**
   * Get the next element in this page of the walk.
   *
   * @return the next element, or null if the walk is finished.
   *
   * @throws MissingObjectException See {@link RevWalk#next()}.
   * @throws IncorrectObjectTypeException See {@link RevWalk#next()}.
   * @throws IOException See {@link RevWalk#next()}.
   */
  public RevCommit next() throws MissingObjectException, IncorrectObjectTypeException, IOException {
    if (done) {
      return null;
    }
    RevCommit commit;
    if (first != null) {
      commit = first;
      first = null;
    } else {
      commit = nextWithRename();
    }
    if (++n == limit) {
      nextStart = nextWithRename();
      done = true;
    } else if (commit == null) {
      done = true;
    }
    return commit;
  }

  private RevCommit nextWithRename() throws IOException {
    RevCommit next = walk.next();
    if (renameWatcher != null) {
      // The commit that triggered the rename isn't available to RenameWatcher,
      // so we can't populate the map from the callback directly. Instead, we
      // need to check after each call to walk.next() whether a rename occurred
      // due to this commit.
      DiffEntry entry = renameWatcher.getAndClear();
      if (entry != null) {
        if (renamed == null) {
          renamed = new HashMap<>();
        }
        renamed.put(next.copy(), entry);
      }
    }
    return next;
  }

  /**
   * @return the ID at the start of the page of results preceding this one, or
   *     null if this is the first page.
   */
  public ObjectId getPreviousStart() {
    return prevStart;
  }

  /**
   * @return the ID at the start of the page of results after this one, or null
   *     if this is the last page.
   */
  public ObjectId getNextStart() {
    checkState(done, "getNextStart() invalid before walk done");
    return nextStart;
  }

  /**
   * @return entry corresponding to a rename or copy at the given commit.
   */
  public DiffEntry getRename(ObjectId commitId) {
    return renamed != null ? renamed.get(commitId) : null;
  }

  /**
   * @return an iterator over the commits in this walk.
   * @throws RevWalkException if an error occurred, wrapping the checked
   *     exception from {@link #next()}.
   */
  @Override
  public Iterator<RevCommit> iterator() {
    return new Iterator<RevCommit>() {
      RevCommit next = nextUnchecked();

      @Override
      public boolean hasNext() {
        return next != null;
      }

      @Override
      public RevCommit next() {
        RevCommit r = next;
        next = nextUnchecked();
        return r;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public int getLimit() {
    return limit;
  }

  public RevWalk getWalk() {
    return walk;
  }

  private RevCommit nextUnchecked() {
    try {
      return next();
    } catch (IOException e) {
      throw new RevWalkException(e);
    }
  }
}
