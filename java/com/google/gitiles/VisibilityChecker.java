/*
 * Copyright (C) 2019, Google LLC.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.gitiles;

import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Checks for object visibility
 *
 * <p>Objects are visible if they are reachable from any of the references visible to the user.
 */
public class VisibilityChecker {

  private final boolean topoSort;

  /**
   * @param topoSort whether to use a more thorough reachability check by sorting in topological
   *     order
   */
  public VisibilityChecker(boolean topoSort) {
    this.topoSort = topoSort;
  }

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
    for (Ref ref : refDb.getRefs()) {
      ref = refDb.peel(ref);
      if (id.equals(ref.getObjectId()) || id.equals(ref.getPeeledObjectId())) {
        return true;
      }
    }

    return false;
  }

  /**
   * Check if {@code commit} is reachable starting from {@code starters}.
   *
   * @param description Description of the ids (e.g. "heads"). Mainly for tracing.
   * @param walk The walk to use for the reachability check
   * @param commit The starting commit. It *MUST* come from the walk in use
   * @param starters visible commits. Anything reachable from these commits is visible. Missing ids
   *     or ids pointing to wrong kind of objects are ignored.
   * @return true if we can get to {@code commit} from the {@code starters}
   * @throws IOException a pack file or loose object could not be read
   */
  protected boolean isReachableFrom(
      String description,
      RevWalk walk,
      RevCommit commit,
      Collection<ObjectId> starters)
      throws IOException {
    if (starters.isEmpty()) {
      return false;
    }

    walk.reset();
    if (topoSort) {
      walk.sort(RevSort.TOPO);
    }

    walk.markStart(commit);
    for (ObjectId id : starters) {
      markUninteresting(walk, id);
    }
    // If the commit is reachable from any given tip, it will appear to be
    // uninteresting to the RevWalk and no output will be produced.
    return walk.next() == null;
  }

  private static void markUninteresting(RevWalk walk, ObjectId id) throws IOException {
    if (id == null) {
      return;
    }
    try {
      walk.markUninteresting(walk.parseCommit(id));
    } catch (IncorrectObjectTypeException | MissingObjectException e) {
      // Do nothing, doesn't affect reachability.
    }
  }
}
