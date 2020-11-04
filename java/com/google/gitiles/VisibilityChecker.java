// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Checks for object visibility
 *
 * <p>Objects are visible if they are reachable from any of the references visible to the user.
 */
public class VisibilityChecker {

  /**
   * Check if any of the refs in {@code refDb} points to the object {@code id}.
   *
   * @param refDb a reference database
   * @param id object we are looking for
   * @return true if the any of the references in the db points directly to the id
   * @throws IOException the reference space cannot be accessed
   */
  protected boolean isTipOfBranch(RefDatabase refDb, ObjectId id) throws IOException {
    // If any reference directly points at the requested object, permit display. Common for displays
    // of pending patch sets in Gerrit Code Review, or bookmarks to the commit a tag points at.
    return !refDb.getTipsWithSha1(id).isEmpty();
  }

  /**
   * Check if {@code commit} is reachable starting from {@code starters}.
   *
   * @param description Description of the ids (e.g. "heads"). Mainly for tracing.
   * @param walk The walk to use for the reachability check
   * @param commit The starting commit. It *MUST* come from the walk in use
   * @param starters visible commits. Anything reachable from these commits is visible. Missing ids
   *     or ids referring to other kinds of objects are ignored.
   * @return true if we can get to {@code commit} from the {@code starters}
   * @throws IOException a pack file or loose object could not be read
   */
  protected boolean isReachableFrom(
      String description, RevWalk walk, RevCommit commit, Collection<ObjectId> starters)
      throws IOException {
    if (starters.isEmpty()) {
      return false;
    }

    ImmutableList<RevCommit> startCommits = objectIdsToCommits(walk, starters);
    if (startCommits.isEmpty()) {
      return false;
    }

    return !walk.createReachabilityChecker()
        .areAllReachable(ImmutableList.of(commit), startCommits)
        .isPresent();
  }

  private static ImmutableList<RevCommit> objectIdsToCommits(RevWalk walk, Collection<ObjectId> ids)
      throws IOException {
    ImmutableList.Builder<RevCommit> commits = ImmutableList.builder();
    for (ObjectId id : ids) {
      try {
        commits.add(walk.parseCommit(id));
      } catch (MissingObjectException e) {
        // TODO(ifrade): ResolveParser has already checked that the object exists in the repo.
        // Report as AssertionError.
      } catch (IncorrectObjectTypeException e) {
        // Ignore, doesn't affect commit reachability
      }
    }
    return commits.build();
  }
}
