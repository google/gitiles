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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.net.HttpHeaders;
import com.google.gitiles.GitilesView.Type;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

import javax.servlet.ServletException;

/** Tests for the view filter. */
@RunWith(JUnit4.class)
public class ViewFilterTest {
  private TestRepository<DfsRepository> repo;

  @Before
  public void setUp() throws Exception {
    repo = new TestRepository<DfsRepository>(
        new InMemoryRepository(new DfsRepositoryDescription("repo")));
  }

  @Test
  public void noCommand() throws Exception {
    assertThat(getView("/").getType()).isEqualTo(Type.HOST_INDEX);
    assertThat(getView("/repo").getType()).isEqualTo(Type.REPOSITORY_INDEX);
    assertThat(getView("/repo/+")).isNull();
    assertThat(getView("/repo/+/")).isNull();
  }

  @Test
  public void autoCommand() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    String hex = master.name();
    String hexBranch = hex.substring(0, 10);
    repo.branch(hexBranch).commit().create();

    assertThat(getView("/repo/+/master").getType()).isEqualTo(Type.REVISION);
    assertThat(getView("/repo/+/" + hexBranch).getType()).isEqualTo(Type.REVISION);
    assertThat(getView("/repo/+/" + hex).getType()).isEqualTo(Type.REVISION);
    assertThat(getView("/repo/+/" + hex.substring(0, 7)).getType()).isEqualTo(Type.REVISION);
    assertThat(getView("/repo/+/master/").getType()).isEqualTo(Type.PATH);
    assertThat(getView("/repo/+/" + hex + "/").getType()).isEqualTo(Type.PATH);
    assertThat(getView("/repo/+/" + hex + "/index.c").getType()).isEqualTo(Type.PATH);
    assertThat(getView("/repo/+/" + hex + "/index.md").getType()).isEqualTo(Type.DOC);
    assertThat(getView("/repo/+/master^..master").getType()).isEqualTo(Type.DIFF);
    assertThat(getView("/repo/+/master^..master/").getType()).isEqualTo(Type.DIFF);
    assertThat(getView("/repo/+/" + parent.name() + ".." + hex + "/").getType())
        .isEqualTo(Type.DIFF);
  }

  @Test
  public void hostIndex() throws Exception {
    GitilesView view = getView("/");
    assertThat(view.getType()).isEqualTo(Type.HOST_INDEX);
    assertThat(view.getHostName()).isEqualTo("test-host");
    assertThat(view.getRepositoryName()).isNull();
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();
  }

  @Test
  public void repositoryIndex() throws Exception {
    GitilesView view = getView("/repo");
    assertThat(view.getType()).isEqualTo(Type.REPOSITORY_INDEX);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();
  }

  @Test
  public void refs() throws Exception {
    GitilesView view;

    view = getView("/repo/+refs");
    assertThat(view.getType()).isEqualTo(Type.REFS);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+refs/");
    assertThat(view.getType()).isEqualTo(Type.REFS);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+refs/heads");
    assertThat(view.getType()).isEqualTo(Type.REFS);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("heads");

    view = getView("/repo/+refs/heads/");
    assertThat(view.getType()).isEqualTo(Type.REFS);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("heads");

    view = getView("/repo/+refs/heads/master");
    assertThat(view.getType()).isEqualTo(Type.REFS);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("heads/master");
  }

  @Test
  public void describe() throws Exception {
    GitilesView view;

    assertThat(getView("/repo/+describe")).isNull();
    assertThat(getView("/repo/+describe/")).isNull();

    view = getView("/repo/+describe/deadbeef");
    assertThat(view.getType()).isEqualTo(Type.DESCRIBE);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("deadbeef");

    view = getView("/repo/+describe/refs/heads/master~3^~2");
    assertThat(view.getType()).isEqualTo(Type.DESCRIBE);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("refs/heads/master~3^~2");
  }

  @Test
  public void showBranches() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    RevCommit stable = repo.branch("refs/heads/stable").commit().create();
    GitilesView view;

    view = getView("/repo/+show/master");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+show/heads/master");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("heads/master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+show/refs/heads/master");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("refs/heads/master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+show/stable");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("stable");
    assertThat(view.getRevision().getId()).isEqualTo(stable);
    assertThat(view.getPathPart()).isNull();

    assertThat(getView("/repo/+show/stable..master")).isNull();
  }

  @Test
  public void ambiguousBranchAndTag() throws Exception {
    RevCommit branch = repo.branch("refs/heads/name").commit().create();
    RevCommit tag = repo.branch("refs/tags/name").commit().create();
    GitilesView view;

    view = getView("/repo/+show/name");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("name");
    assertThat(view.getRevision().getId()).isEqualTo(tag);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+show/heads/name");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("heads/name");
    assertThat(view.getRevision().getId()).isEqualTo(branch);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+show/refs/heads/name");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("refs/heads/name");
    assertThat(view.getRevision().getId()).isEqualTo(branch);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+show/tags/name");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("tags/name");
    assertThat(view.getRevision().getId()).isEqualTo(tag);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+show/refs/tags/name");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getRevision().getName()).isEqualTo("refs/tags/name");
    assertThat(view.getRevision().getId()).isEqualTo(tag);
    assertThat(view.getPathPart()).isNull();
  }

  @Test
  public void path() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    repo.branch("refs/heads/stable").commit().create();
    GitilesView view;

    view = getView("/repo/+show/master/");
    assertThat(view.getType()).isEqualTo(Type.PATH);
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+show/master/foo");
    assertThat(view.getType()).isEqualTo(Type.PATH);
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isEqualTo("foo");

    view = getView("/repo/+show/master/foo/");
    assertThat(view.getType()).isEqualTo(Type.PATH);
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isEqualTo("foo");

    view = getView("/repo/+show/master/foo/bar");
    assertThat(view.getType()).isEqualTo(Type.PATH);
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isEqualTo("foo/bar");

    assertThat(getView("/repo/+show/stable..master/foo")).isNull();
  }

  @Test
  public void doc() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    repo.branch("refs/heads/stable").commit().create();
    GitilesView view;

    view = getView("/repo/+doc/master/");
    assertThat(view.getType()).isEqualTo(Type.DOC);
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+doc/master/index.md");
    assertThat(view.getType()).isEqualTo(Type.DOC);
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isEqualTo("index.md");

    view = getView("/repo/+doc/master/foo/");
    assertThat(view.getType()).isEqualTo(Type.DOC);
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isEqualTo("foo");

    view = getView("/repo/+doc/master/foo/bar.md");
    assertThat(view.getType()).isEqualTo(Type.DOC);
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getPathPart()).isEqualTo("foo/bar.md");

    assertThat(getView("/repo/+doc/stable..master/foo")).isNull();
  }

  @Test
  public void multipleSlashes() throws Exception {
    repo.branch("refs/heads/master").commit().create();
    assertThat(getView("//").getType()).isEqualTo(Type.HOST_INDEX);
    assertThat(getView("//repo").getType()).isEqualTo(Type.REPOSITORY_INDEX);
    assertThat(getView("//repo//").getType()).isEqualTo(Type.REPOSITORY_INDEX);
    assertThat(getView("/repo/+//master")).isNull();
    assertThat(getView("/repo/+/refs//heads//master")).isNull();
    assertThat(getView("/repo/+//master//")).isNull();
    assertThat(getView("/repo/+//master/foo//bar")).isNull();
  }

  @Test
  public void diff() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    GitilesView view;

    view = getView("/repo/+diff/master^..master");
    assertThat(view.getType()).isEqualTo(Type.DIFF);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+diff/master^..master/");
    assertThat(view.getType()).isEqualTo(Type.DIFF);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+diff/master^..master/foo");
    assertThat(view.getType()).isEqualTo(Type.DIFF);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getPathPart()).isEqualTo("foo");

    view = getView("/repo/+diff/refs/heads/master^..refs/heads/master");
    assertThat(view.getType()).isEqualTo(Type.DIFF);
    assertThat(view.getRevision().getName()).isEqualTo("refs/heads/master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision().getName()).isEqualTo("refs/heads/master^");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getPathPart()).isEqualTo("");
  }

  @Test
  public void diffAgainstEmptyCommit() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    GitilesView view = getView("/repo/+diff/master^!");
    assertThat(view.getType()).isEqualTo(Type.DIFF);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("");
  }

  @Test
  public void log() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    GitilesView view;

    view = getView("/repo/+log");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+log/");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+log/master");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+log/master/");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+log/master/foo");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("foo");

    view = getView("/repo/+log/master^..master");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+log/master^..master/");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getPathPart()).isEqualTo("");

    view = getView("/repo/+log/master^..master/foo");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getPathPart()).isEqualTo("foo");

    view = getView("/repo/+log/refs/heads/master^..refs/heads/master");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getRevision().getName()).isEqualTo("refs/heads/master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision().getName()).isEqualTo("refs/heads/master^");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getPathPart()).isEqualTo("");
  }

  @Test
  public void archive() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    repo.branch("refs/heads/branch").commit().create();
    GitilesView view;

    assertThat(getView("/repo/+archive")).isNull();
    assertThat(getView("/repo/+archive/")).isNull();
    assertThat(getView("/repo/+archive/master..branch")).isNull();
    assertThat(getView("/repo/+archive/master.foo")).isNull();
    assertThat(getView("/repo/+archive/master.zip")).isNull();
    assertThat(getView("/repo/+archive/master/.tar.gz")).isNull();
    assertThat(getView("/repo/+archive/master/foo/.tar.gz")).isNull();

    view = getView("/repo/+archive/master.tar.gz");
    assertThat(view.getType()).isEqualTo(Type.ARCHIVE);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getExtension()).isEqualTo(".tar.gz");
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+archive/master.tar.bz2");
    assertThat(view.getType()).isEqualTo(Type.ARCHIVE);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getExtension()).isEqualTo(".tar.bz2");
    assertThat(view.getPathPart()).isNull();

    view = getView("/repo/+archive/master/foo/bar.tar.gz");
    assertThat(view.getType()).isEqualTo(Type.ARCHIVE);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getExtension()).isEqualTo(".tar.gz");
    assertThat(view.getPathPart()).isEqualTo("foo/bar");
  }

  @Test
  public void blame() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    repo.branch("refs/heads/branch").commit().create();
    GitilesView view;

    assertThat(getView("/repo/+blame")).isNull();
    assertThat(getView("/repo/+blame/")).isNull();
    assertThat(getView("/repo/+blame/master")).isNull();
    assertThat(getView("/repo/+blame/master..branch")).isNull();

    view = getView("/repo/+blame/master/foo/bar");
    assertThat(view.getType()).isEqualTo(Type.BLAME);
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getRevision().getId()).isEqualTo(master);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("foo/bar");
  }

  @Test
  public void testNormalizeParents() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    GitilesView view;

    assertThat(getView("/repo/+/master").toUrl()).isEqualTo("/b/repo/+/master");
    assertThat(getView("/repo/+/" + master.name()).toUrl()).isEqualTo("/b/repo/+/" + master.name());
    assertThat(getRedirectUrl("/repo/+/master~")).isEqualTo("/b/repo/+/" + parent.name());
    assertThat(getRedirectUrl("/repo/+/master^")).isEqualTo("/b/repo/+/" + parent.name());

    view = getView("/repo/+log/master~..master/");
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision().getName()).isEqualTo("master~");

    view = getView("/repo/+log/master^!/");
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
  }

  private String getRedirectUrl(String pathAndQuery) throws ServletException, IOException {
    TestViewFilter.Result result = TestViewFilter.service(repo, pathAndQuery);
    assertThat(result.getResponse().getStatus()).isEqualTo(302);
    return result.getResponse().getHeader(HttpHeaders.LOCATION);
  }

  private GitilesView getView(String pathAndQuery) throws ServletException, IOException {
    TestViewFilter.Result result = TestViewFilter.service(repo, pathAndQuery);
    FakeHttpServletResponse resp = result.getResponse();
    assertWithMessage("expected non-redirect status, got " + resp.getStatus())
        .that(resp.getStatus() < 300 || resp.getStatus() >= 400).isTrue();
    return result.getView();
  }
}
