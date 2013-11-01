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

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

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
  private final RevWalk walk;
  private final ObjectId start;
  private final int limit;
  private final Deque<ObjectId> prevBuffer;

  private boolean done;
  private int i;
  private int n;
  private int foundIndex;
  private ObjectId nextStart;

  /**
   * @param walk revision walk.
   * @param limit page size.
   * @param start commit at which to start the walk, or null to start at the
   *     beginning.
   */
  Paginator(RevWalk walk, int limit, @Nullable ObjectId start) {
    this.walk = checkNotNull(walk, "walk");
    this.start = start;
    checkArgument(limit > 0, "limit must be positive: %s", limit);
    this.limit = limit;
    prevBuffer = new ArrayDeque<ObjectId>(start != null ? limit : 0);
    i = -1;
    foundIndex = -1;
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
  public RevCommit next() throws MissingObjectException, IncorrectObjectTypeException,
      IOException {
    RevCommit commit;
    if (foundIndex < 0) {
      while (true) {
        commit = walk.next();
        if (commit == null) {
          done = true;
          return null;
        }
        i++;
        if (start == null || start.equals(commit)) {
          foundIndex = i;
          break;
        }
        if (prevBuffer.size() == limit) {
          prevBuffer.remove();
        }
        prevBuffer.add(commit);
      }
    } else {
      commit = walk.next();
    }

    if (++n == limit) {
      done = true;
    } else if (n == limit + 1 || commit == null) {
      nextStart = commit;
      done = true;
      return null;
    }
    return commit;
  }

  /**
   * @return the ID at the start of the page of results preceding this one, or
   *     null if this is the first page.
   */
  public ObjectId getPreviousStart() {
    checkState(done, "getPreviousStart() invalid before walk done");
    return prevBuffer.pollFirst();
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
    } catch (MissingObjectException e) {
      throw new RevWalkException(e);
    } catch (IncorrectObjectTypeException e) {
      throw new RevWalkException(e);
    } catch (IOException e) {
      throw new RevWalkException(e);
    }
  }
}
