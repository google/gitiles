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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    assertEquals(Type.HOST_INDEX, getView("/").getType());
    assertEquals(Type.REPOSITORY_INDEX, getView("/repo").getType());
    assertNull(getView("/repo/+"));
    assertNull(getView("/repo/+/"));
  }

  @Test
  public void autoCommand() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    String hex = master.name();
    String hexBranch = hex.substring(0, 10);
    repo.branch(hexBranch).commit().create();

    assertEquals(Type.REVISION, getView("/repo/+/master").getType());
    assertEquals(Type.REVISION, getView("/repo/+/" + hexBranch).getType());
    assertEquals(Type.REVISION, getView("/repo/+/" + hex).getType());
    assertEquals(Type.REVISION, getView("/repo/+/" + hex.substring(0, 7)).getType());
    assertEquals(Type.PATH, getView("/repo/+/master/").getType());
    assertEquals(Type.PATH, getView("/repo/+/" + hex + "/").getType());
    assertEquals(Type.PATH, getView("/repo/+/" + hex + "/index.c").getType());
    assertEquals(Type.DOC, getView("/repo/+/" + hex + "/index.md").getType());
    assertEquals(Type.DIFF, getView("/repo/+/master^..master").getType());
    assertEquals(Type.DIFF, getView("/repo/+/master^..master/").getType());
    assertEquals(Type.DIFF, getView("/repo/+/" + parent.name() + ".." + hex + "/").getType());
  }

  @Test
  public void hostIndex() throws Exception {
    GitilesView view = getView("/");
    assertEquals(Type.HOST_INDEX, view.getType());
    assertEquals("test-host", view.getHostName());
    assertNull(view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertNull(view.getPathPart());
  }

  @Test
  public void repositoryIndex() throws Exception {
    GitilesView view = getView("/repo");
    assertEquals(Type.REPOSITORY_INDEX, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertNull(view.getPathPart());
  }

  @Test
  public void refs() throws Exception {
    GitilesView view;

    view = getView("/repo/+refs");
    assertEquals(Type.REFS, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+refs/");
    assertEquals(Type.REFS, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+refs/heads");
    assertEquals(Type.REFS, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("heads", view.getPathPart());

    view = getView("/repo/+refs/heads/");
    assertEquals(Type.REFS, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("heads", view.getPathPart());

    view = getView("/repo/+refs/heads/master");
    assertEquals(Type.REFS, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("heads/master", view.getPathPart());
  }

  @Test
  public void describe() throws Exception {
    GitilesView view;

    assertNull(getView("/repo/+describe"));
    assertNull(getView("/repo/+describe/"));

    view = getView("/repo/+describe/deadbeef");
    assertEquals(Type.DESCRIBE, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("deadbeef", view.getPathPart());

    view = getView("/repo/+describe/refs/heads/master~3^~2");
    assertEquals(Type.DESCRIBE, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("refs/heads/master~3^~2", view.getPathPart());
  }

  @Test
  public void showBranches() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    RevCommit stable = repo.branch("refs/heads/stable").commit().create();
    GitilesView view;

    view = getView("/repo/+show/master");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertNull(view.getPathPart());

    view = getView("/repo/+show/heads/master");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("heads/master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertNull(view.getPathPart());

    view = getView("/repo/+show/refs/heads/master");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("refs/heads/master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertNull(view.getPathPart());

    view = getView("/repo/+show/stable");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("stable", view.getRevision().getName());
    assertEquals(stable, view.getRevision().getId());
    assertNull(view.getPathPart());

    assertNull(getView("/repo/+show/stable..master"));
  }

  @Test
  public void ambiguousBranchAndTag() throws Exception {
    RevCommit branch = repo.branch("refs/heads/name").commit().create();
    RevCommit tag = repo.branch("refs/tags/name").commit().create();
    GitilesView view;

    view = getView("/repo/+show/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("name", view.getRevision().getName());
    assertEquals(tag, view.getRevision().getId());
    assertNull(view.getPathPart());

    view = getView("/repo/+show/heads/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("heads/name", view.getRevision().getName());
    assertEquals(branch, view.getRevision().getId());
    assertNull(view.getPathPart());

    view = getView("/repo/+show/refs/heads/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("refs/heads/name", view.getRevision().getName());
    assertEquals(branch, view.getRevision().getId());
    assertNull(view.getPathPart());

    view = getView("/repo/+show/tags/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("tags/name", view.getRevision().getName());
    assertEquals(tag, view.getRevision().getId());
    assertNull(view.getPathPart());

    view = getView("/repo/+show/refs/tags/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("refs/tags/name", view.getRevision().getName());
    assertEquals(tag, view.getRevision().getId());
    assertNull(view.getPathPart());
  }

  @Test
  public void path() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    repo.branch("refs/heads/stable").commit().create();
    GitilesView view;

    view = getView("/repo/+show/master/");
    assertEquals(Type.PATH, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+show/master/foo");
    assertEquals(Type.PATH, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("foo", view.getPathPart());

    view = getView("/repo/+show/master/foo/");
    assertEquals(Type.PATH, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("foo", view.getPathPart());

    view = getView("/repo/+show/master/foo/bar");
    assertEquals(Type.PATH, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("foo/bar", view.getPathPart());

    assertNull(getView("/repo/+show/stable..master/foo"));
  }

  @Test
  public void doc() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    repo.branch("refs/heads/stable").commit().create();
    GitilesView view;

    view = getView("/repo/+doc/master/");
    assertEquals(Type.DOC, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+doc/master/index.md");
    assertEquals(Type.DOC, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("index.md", view.getPathPart());

    view = getView("/repo/+doc/master/foo/");
    assertEquals(Type.DOC, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("foo", view.getPathPart());

    view = getView("/repo/+doc/master/foo/bar.md");
    assertEquals(Type.DOC, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("foo/bar.md", view.getPathPart());

    assertNull(getView("/repo/+doc/stable..master/foo"));
  }

  @Test
  public void multipleSlashes() throws Exception {
    repo.branch("refs/heads/master").commit().create();
    assertEquals(Type.HOST_INDEX, getView("//").getType());
    assertEquals(Type.REPOSITORY_INDEX, getView("//repo").getType());
    assertEquals(Type.REPOSITORY_INDEX, getView("//repo//").getType());
    assertNull(getView("/repo/+//master"));
    assertNull(getView("/repo/+/refs//heads//master"));
    assertNull(getView("/repo/+//master//"));
    assertNull(getView("/repo/+//master/foo//bar"));
  }

  @Test
  public void diff() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    GitilesView view;

    view = getView("/repo/+diff/master^..master");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+diff/master^..master/");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+diff/master^..master/foo");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("foo", view.getPathPart());

    view = getView("/repo/+diff/refs/heads/master^..refs/heads/master");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("refs/heads/master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("refs/heads/master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getPathPart());
  }

  @Test
  public void diffAgainstEmptyCommit() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    GitilesView view = getView("/repo/+diff/master^!");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("", view.getPathPart());
  }

  @Test
  public void log() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    GitilesView view;

    view = getView("/repo/+log");
    assertEquals(Type.LOG, view.getType());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getPathPart());

    view = getView("/repo/+log/");
    assertEquals(Type.LOG, view.getType());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getPathPart());

    view = getView("/repo/+log/master");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+log/master/");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+log/master/foo");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("foo", view.getPathPart());

    view = getView("/repo/+log/master^..master");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+log/master^..master/");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getPathPart());

    view = getView("/repo/+log/master^..master/foo");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("foo", view.getPathPart());

    view = getView("/repo/+log/refs/heads/master^..refs/heads/master");
    assertEquals(Type.LOG, view.getType());
    assertEquals("refs/heads/master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("refs/heads/master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getPathPart());
  }

  @Test
  public void archive() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    repo.branch("refs/heads/branch").commit().create();
    GitilesView view;

    assertNull(getView("/repo/+archive"));
    assertNull(getView("/repo/+archive/"));
    assertNull(getView("/repo/+archive/master..branch"));
    assertNull(getView("/repo/+archive/master.foo"));
    assertNull(getView("/repo/+archive/master.zip"));
    assertNull(getView("/repo/+archive/master/.tar.gz"));
    assertNull(getView("/repo/+archive/master/foo/.tar.gz"));

    view = getView("/repo/+archive/master.tar.gz");
    assertEquals(Type.ARCHIVE, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals(".tar.gz", view.getExtension());
    assertNull(view.getPathPart());

    view = getView("/repo/+archive/master.tar.bz2");
    assertEquals(Type.ARCHIVE, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals(".tar.bz2", view.getExtension());
    assertNull(view.getPathPart());

    view = getView("/repo/+archive/master/foo/bar.tar.gz");
    assertEquals(Type.ARCHIVE, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals(".tar.gz", view.getExtension());
    assertEquals("foo/bar", view.getPathPart());
  }

  @Test
  public void blame() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    repo.branch("refs/heads/branch").commit().create();
    GitilesView view;

    assertNull(getView("/repo/+blame"));
    assertNull(getView("/repo/+blame/"));
    assertNull(getView("/repo/+blame/master"));
    assertNull(getView("/repo/+blame/master..branch"));

    view = getView("/repo/+blame/master/foo/bar");
    assertEquals(Type.BLAME, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("foo/bar", view.getPathPart());
  }

  @Test
  public void testNormalizeParents() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    GitilesView view;

    assertEquals("/b/repo/+/master", getView("/repo/+/master").toUrl());
    assertEquals("/b/repo/+/" + master.name(), getView("/repo/+/" + master.name()).toUrl());
    assertEquals("/b/repo/+/" + parent.name(), getRedirectUrl("/repo/+/master~"));
    assertEquals("/b/repo/+/" + parent.name(), getRedirectUrl("/repo/+/master^"));

    view = getView("/repo/+log/master~..master/");
    assertEquals("master", view.getRevision().getName());
    assertEquals("master~", view.getOldRevision().getName());

    view = getView("/repo/+log/master^!/");
    assertEquals("master", view.getRevision().getName());
    assertEquals("master^", view.getOldRevision().getName());
  }

  private String getRedirectUrl(String pathAndQuery) throws ServletException, IOException {
    TestViewFilter.Result result = TestViewFilter.service(repo, pathAndQuery);
    assertEquals(302, result.getResponse().getStatus());
    return result.getResponse().getHeader(HttpHeaders.LOCATION);
  }

  private GitilesView getView(String pathAndQuery) throws ServletException, IOException {
    TestViewFilter.Result result = TestViewFilter.service(repo, pathAndQuery);
    FakeHttpServletResponse resp = result.getResponse();
    assertTrue("expected non-redirect status, got " + resp.getStatus(),
        resp.getStatus() < 300 || resp.getStatus() >= 400);
    return result.getView();
  }
}
