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

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VisibilityCacheTest {

  private InMemoryRepository repo;
  private GitilesAccess access = new FakeGitilesAccess();

  private RevCommit baseCommit;
  private RevCommit commit1;
  private RevCommit commit2;
  private RevCommit commitA;
  private RevCommit commitB;
  private RevCommit commitC;

  private VisibilityCache visibilityCache;
  private RevWalk walk;

  @Before
  public void setUp() throws Exception {
    /**
     *
     *
     * <pre>
     *               commitC
     *                 |
     *   commit2     commitB
     *      |          |
     *   commit1     commitA <--- refs/tags/v0.1
     *       \         /
     *        \       /
     *        baseCommit
     * </pre>
     */
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

    visibilityCache = new VisibilityCache(true);
    walk = new RevWalk(repo);
    walk.setRetainBody(false);
  }

  @After
  public void tearDown() {
    repo.close();
  }

  @Test
  public void isTip() throws IOException {
    ObjectId[] known = new ObjectId[0];
    assertThat(visibilityCache.isVisible(repo, walk, access, commit2.getId(), known)).isTrue();
  }

  @Test
  public void reachableFromHeads() throws Exception {
    ObjectId[] known = new ObjectId[0];
    assertThat(visibilityCache.isVisible(repo, walk, access, commit1.getId(), known)).isTrue();
  }

  @Test
  public void reachableFromTags() throws Exception {
    ObjectId[] known = new ObjectId[0];
    assertThat(visibilityCache.isVisible(repo, walk, access, commitA.getId(), known)).isTrue();
  }

  @Test
  public void unreachableFromAnyTip() throws Exception {
    ObjectId[] known = new ObjectId[0];
    assertThat(visibilityCache.isVisible(repo, walk, access, commitB.getId(), known)).isFalse();
  }

  @Test
  public void reachableFromAnotherId() throws Exception {
    ObjectId[] known = new ObjectId[] {commitC.getId()};
    assertThat(visibilityCache.isVisible(repo, walk, access, commitB.getId(), known)).isTrue();
  }

  private static class FakeGitilesAccess implements GitilesAccess {
    @Override
    public Map<String, RepositoryDescription> listRepositories(String prefix, Set<String> branches)
        throws ServiceNotEnabledException, ServiceNotAuthorizedException, IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getUserKey() {
      return "Test";
    }

    @Override
    public String getRepositoryName() {
      return "VisibilityCache-Test";
    }

    @Override
    public RepositoryDescription getRepositoryDescription() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Config getConfig() throws IOException {
      throw new UnsupportedOperationException();
    }
  }
}
