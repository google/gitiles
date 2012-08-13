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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dfs.DfsRepository;
import org.eclipse.jgit.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.storage.dfs.InMemoryRepository;

import java.util.List;

/** Unit tests for {@link LogServlet}. */
public class PaginatorTest extends TestCase {
  private TestRepository<DfsRepository> repo;
  private RevWalk walk;

  @Override
  protected void setUp() throws Exception {
    repo = new TestRepository<DfsRepository>(
        new InMemoryRepository(new DfsRepositoryDescription("test")));
    walk = new RevWalk(repo.getRepository());
  }

  @Override
  protected void tearDown() throws Exception {
    walk.release();
  }

  public void testStart() throws Exception {
    List<RevCommit> commits = linearCommits(10);
    walk.markStart(commits.get(9));
    Paginator p = new Paginator(walk, 3, commits.get(9));
    assertEquals(
        ImmutableList.of(
            commits.get(9),
            commits.get(8),
            commits.get(7)),
        ImmutableList.copyOf(p));
    assertNull(p.getPreviousStart());
    assertEquals(commits.get(6), p.getNextStart());
  }

  public void testNoStartCommit() throws Exception {
    List<RevCommit> commits = linearCommits(10);
    walk.markStart(commits.get(9));
    Paginator p = new Paginator(walk, 3, null);
    assertEquals(
        ImmutableList.of(
            commits.get(9),
            commits.get(8),
            commits.get(7)),
        ImmutableList.copyOf(p));
    assertNull(p.getPreviousStart());
    assertEquals(commits.get(6), p.getNextStart());
  }

  public void testLessThanOnePageIn() throws Exception {
    List<RevCommit> commits = linearCommits(10);
    walk.markStart(commits.get(9));
    Paginator p = new Paginator(walk, 3, commits.get(8));
    assertEquals(
        ImmutableList.of(
            commits.get(8),
            commits.get(7),
            commits.get(6)),
        ImmutableList.copyOf(p));
    assertEquals(commits.get(9), p.getPreviousStart());
    assertEquals(commits.get(5), p.getNextStart());
  }

  public void testAtLeastOnePageIn() throws Exception {
    List<RevCommit> commits = linearCommits(10);
    walk.markStart(commits.get(9));
    Paginator p = new Paginator(walk, 3, commits.get(7));
    assertEquals(
        ImmutableList.of(
            commits.get(7),
            commits.get(6),
            commits.get(5)),
        ImmutableList.copyOf(p));
    assertEquals(commits.get(9), p.getPreviousStart());
    assertEquals(commits.get(4), p.getNextStart());
  }

  public void testEnd() throws Exception {
    List<RevCommit> commits = linearCommits(10);
    walk.markStart(commits.get(9));
    Paginator p = new Paginator(walk, 3, commits.get(2));
    assertEquals(
        ImmutableList.of(
            commits.get(2),
            commits.get(1),
            commits.get(0)),
        ImmutableList.copyOf(p));
    assertEquals(commits.get(5), p.getPreviousStart());
    assertNull(p.getNextStart());
  }

  public void testOnePastEnd() throws Exception {
    List<RevCommit> commits = linearCommits(10);
    walk.markStart(commits.get(9));
    Paginator p = new Paginator(walk, 3, commits.get(1));
    assertEquals(
        ImmutableList.of(
            commits.get(1),
            commits.get(0)),
        ImmutableList.copyOf(p));
    assertEquals(commits.get(4), p.getPreviousStart());
    assertNull(p.getNextStart());
  }

  public void testManyPastEnd() throws Exception {
    List<RevCommit> commits = linearCommits(10);
    walk.markStart(commits.get(9));
    Paginator p = new Paginator(walk, 5, commits.get(1));
    assertEquals(
        ImmutableList.of(
            commits.get(1),
            commits.get(0)),
        ImmutableList.copyOf(p));
    assertEquals(commits.get(6), p.getPreviousStart());
    assertNull(p.getNextStart());
  }

  private List<RevCommit> linearCommits(int n) throws Exception {
    checkArgument(n > 0);
    List<RevCommit> commits = Lists.newArrayList();
    commits.add(repo.commit().create());
    for (int i = 1; i < 10; i++) {
      commits.add(repo.commit().parent(commits.get(commits.size() - 1)).create());
    }
    return commits;
  }
}
