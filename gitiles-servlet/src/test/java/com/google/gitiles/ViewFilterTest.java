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

import static com.google.gitiles.FakeHttpServletRequest.newRequest;
import static com.google.gitiles.GitilesFilter.REPO_PATH_REGEX;
import static com.google.gitiles.GitilesFilter.REPO_REGEX;
import static com.google.gitiles.GitilesFilter.ROOT_REGEX;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Atomics;
import com.google.gitiles.GitilesView.Type;

import junit.framework.TestCase;

import org.eclipse.jgit.http.server.glue.MetaFilter;
import org.eclipse.jgit.http.server.glue.MetaServlet;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.dfs.DfsRepository;
import org.eclipse.jgit.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.storage.dfs.InMemoryRepository;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Tests for the view filter. */
public class ViewFilterTest extends TestCase {
  private TestRepository<DfsRepository> repo;

  @Override
  protected void setUp() throws Exception {
    repo = new TestRepository<DfsRepository>(
        new InMemoryRepository(new DfsRepositoryDescription("test")));
  }

  public void testNoCommand() throws Exception {
    assertEquals(Type.HOST_INDEX, getView("/").getType());
    assertEquals(Type.REPOSITORY_INDEX, getView("/repo").getType());
    assertNull(getView("/repo/+"));
    assertNull(getView("/repo/+/"));
  }

  public void testAutoCommand() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    String hex = master.name();
    String hexBranch = hex.substring(0, 10);
    repo.branch(hexBranch).commit().create();

    assertEquals(Type.LOG, getView("/repo/+/master").getType());
    assertEquals(Type.LOG, getView("/repo/+/" + hexBranch).getType());
    assertEquals(Type.REVISION, getView("/repo/+/" + hex).getType());
    assertEquals(Type.REVISION, getView("/repo/+/" + hex.substring(0, 7)).getType());
    assertEquals(Type.PATH, getView("/repo/+/master/").getType());
    assertEquals(Type.PATH, getView("/repo/+/" + hex + "/").getType());
    assertEquals(Type.DIFF, getView("/repo/+/master^..master").getType());
    assertEquals(Type.DIFF, getView("/repo/+/master^..master/").getType());
    assertEquals(Type.DIFF, getView("/repo/+/" + parent.name() + ".." + hex + "/").getType());
  }

  public void testHostIndex() throws Exception {
    GitilesView view = getView("/");
    assertEquals(Type.HOST_INDEX, view.getType());
    assertEquals("test-host", view.getHostName());
    assertNull(view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertNull(view.getTreePath());
  }

  public void testRepositoryIndex() throws Exception {
    GitilesView view = getView("/repo");
    assertEquals(Type.REPOSITORY_INDEX, view.getType());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertNull(view.getTreePath());
  }

  public void testBranches() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    RevCommit stable = repo.branch("refs/heads/stable").commit().create();
    GitilesView view;

    view = getView("/repo/+show/master");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertNull(view.getTreePath());

    view = getView("/repo/+show/heads/master");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("heads/master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertNull(view.getTreePath());

    view = getView("/repo/+show/refs/heads/master");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("refs/heads/master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertNull(view.getTreePath());

    view = getView("/repo/+show/stable");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("stable", view.getRevision().getName());
    assertEquals(stable, view.getRevision().getId());
    assertNull(view.getTreePath());
  }

  public void testAmbiguousBranchAndTag() throws Exception {
    RevCommit branch = repo.branch("refs/heads/name").commit().create();
    RevCommit tag = repo.branch("refs/tags/name").commit().create();
    GitilesView view;

    view = getView("/repo/+show/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("name", view.getRevision().getName());
    assertEquals(tag, view.getRevision().getId());
    assertNull(view.getTreePath());

    view = getView("/repo/+show/heads/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("heads/name", view.getRevision().getName());
    assertEquals(branch, view.getRevision().getId());
    assertNull(view.getTreePath());

    view = getView("/repo/+show/refs/heads/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("refs/heads/name", view.getRevision().getName());
    assertEquals(branch, view.getRevision().getId());
    assertNull(view.getTreePath());

    view = getView("/repo/+show/tags/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("tags/name", view.getRevision().getName());
    assertEquals(tag, view.getRevision().getId());
    assertNull(view.getTreePath());

    view = getView("/repo/+show/refs/tags/name");
    assertEquals(Type.REVISION, view.getType());
    assertEquals("refs/tags/name", view.getRevision().getName());
    assertEquals(tag, view.getRevision().getId());
    assertNull(view.getTreePath());
  }

  public void testPath() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    GitilesView view;

    view = getView("/repo/+show/master/");
    assertEquals(Type.PATH, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("", view.getTreePath());

    view = getView("/repo/+show/master/foo");
    assertEquals(Type.PATH, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("foo", view.getTreePath());

    view = getView("/repo/+show/master/foo/");
    assertEquals(Type.PATH, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("foo", view.getTreePath());

    view = getView("/repo/+show/master/foo/bar");
    assertEquals(Type.PATH, view.getType());
    assertEquals(master, view.getRevision().getId());
    assertEquals("foo/bar", view.getTreePath());
  }

  public void testMultipleSlashes() throws Exception {
    repo.branch("refs/heads/master").commit().create();
    assertEquals(Type.HOST_INDEX, getView("//").getType());
    assertEquals(Type.REPOSITORY_INDEX, getView("//repo").getType());
    assertEquals(Type.REPOSITORY_INDEX, getView("//repo//").getType());
    assertNull(getView("/repo/+//master"));
    assertNull(getView("/repo/+/refs//heads//master"));
    assertNull(getView("/repo/+//master//"));
    assertNull(getView("/repo/+//master/foo//bar"));
  }

  public void testDiff() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    GitilesView view;

    view = getView("/repo/+diff/master^..master");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getTreePath());

    view = getView("/repo/+diff/master^..master/");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getTreePath());

    view = getView("/repo/+diff/master^..master/foo");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("foo", view.getTreePath());

    view = getView("/repo/+diff/refs/heads/master^..refs/heads/master");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("refs/heads/master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("refs/heads/master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getTreePath());
  }

  public void testDiffAgainstEmptyCommit() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    GitilesView view = getView("/repo/+diff/master^!");
    assertEquals(Type.DIFF, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("", view.getTreePath());
  }

  public void testLog() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit master = repo.branch("refs/heads/master").commit().parent(parent).create();
    GitilesView view;

    assertNull(getView("/repo/+log"));
    assertNull(getView("/repo/+log/"));

    view = getView("/repo/+log/master");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("", view.getTreePath());

    view = getView("/repo/+log/master/");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("", view.getTreePath());

    view = getView("/repo/+log/master/foo");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("foo", view.getTreePath());

    view = getView("/repo/+log/master^..master");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getTreePath());

    view = getView("/repo/+log/master^..master/");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getTreePath());

    view = getView("/repo/+log/master^..master/foo");
    assertEquals(Type.LOG, view.getType());
    assertEquals("master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("foo", view.getTreePath());

    view = getView("/repo/+log/refs/heads/master^..refs/heads/master");
    assertEquals(Type.LOG, view.getType());
    assertEquals("refs/heads/master", view.getRevision().getName());
    assertEquals(master, view.getRevision().getId());
    assertEquals("refs/heads/master^", view.getOldRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("", view.getTreePath());
  }

  private GitilesView getView(String pathAndQuery) throws ServletException, IOException {
    final AtomicReference<GitilesView> view = Atomics.newReference();
    HttpServlet testServlet = new HttpServlet() {
      private static final long serialVersionUID = 1L;
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        view.set(ViewFilter.getView(req));
      }
    };

    ViewFilter vf = new ViewFilter(
        new TestGitilesAccess(repo.getRepository()),
        TestGitilesUrls.URLS,
        new VisibilityCache(false));
    MetaFilter mf = new MetaFilter();

    for (Pattern p : ImmutableList.of(ROOT_REGEX, REPO_REGEX, REPO_PATH_REGEX)) {
      mf.serveRegex(p)
          .through(vf)
          .with(testServlet);
    }

    FakeHttpServletRequest req = newRequest(repo.getRepository());
    int q = pathAndQuery.indexOf('?');
    if (q > 0) {
      req.setPathInfo(pathAndQuery.substring(0, q));
      req.setQueryString(pathAndQuery.substring(q + 1));
    } else {
      req.setPathInfo(pathAndQuery);
    }
    new MetaServlet(mf){}.service(req, new FakeHttpServletResponse());

    return view.get();
  }
}
