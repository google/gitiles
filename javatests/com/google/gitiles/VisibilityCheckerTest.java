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
    try (TestRepository<InMemoryRepository> git = new TestRepository<>(repo)) {
      baseCommit = git.commit().message("baseCommit").create();
      commit1 = git.commit().parent(baseCommit).message("commit1").create();
      commit2 = git.commit().parent(commit1).message("commit2").create();

      commitA = git.commit().parent(baseCommit).message("commitA").create();
      commitB = git.commit().parent(commitA).message("commitB").create();
      commitC = git.commit().parent(commitB).message("commitC").create();

      git.update("master", commit2);
      git.update("refs/tags/v0.1", commitA);
    }

    visibilityChecker = new VisibilityChecker();
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
