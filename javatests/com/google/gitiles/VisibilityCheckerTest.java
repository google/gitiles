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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VisibilityCheckerTest {
  private InMemoryRepository repo;

  private RevCommit baseCommit;
  private RevCommit commit1;
  private RevCommit commit2;
  private RevCommit commitA;
  private RevCommit commitB;
  private RevCommit commitC;

  private VisibilityChecker visibilityChecker;
  private RevWalk walk;

  @Before
  public void setUp() throws Exception {
    repo = new InMemoryRepository(new DfsRepositoryDescription());
    TestRepository<InMemoryRepository> git = new TestRepository<>(repo);
    baseCommit = git.commit().message("baseCommit").create();
    commit1 = git.commit().parent(baseCommit).message("commit1").create();
    commit2 = git.commit().parent(commit1).message("commit2").create();

    commitA = git.commit().parent(baseCommit).message("commitA").create();
    commitB = git.commit().parent(commitA).message("commitB").create();
    commitC = git.commit().parent(commitB).message("commitC").create();

    git.update("master", commit2);
    git.update("refs/tags/v0.1", commitA);

    visibilityChecker = new VisibilityChecker(true);
    walk = new RevWalk(repo);
    walk.setRetainBody(false);
  }

  @Test
  public void isTip() throws IOException {
    assertTrue(visibilityChecker.isTipOfBranch(repo.getRefDatabase(), commit2.getId()));
  }

  @Test
  public void isNotTip() throws IOException {
    assertFalse(visibilityChecker.isTipOfBranch(repo.getRefDatabase(), commit1.getId()));
  }

  @Test
  public void reachableFromRef() throws IOException {
    List<ObjectId> starters = Arrays.asList(commitC.getId());
    assertTrue(
        visibilityChecker.isReachableFrom("test", walk, walk.parseCommit(commitB), starters));
  }

  @Test
  public void unreachableFromRef() throws IOException {
    List<ObjectId> starters = Arrays.asList(commit2.getId(), commitA.getId());
    assertFalse(
        visibilityChecker.isReachableFrom("test", walk, walk.parseCommit(commitC), starters));
  }
}
