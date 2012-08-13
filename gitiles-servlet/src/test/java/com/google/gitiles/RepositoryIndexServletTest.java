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

import static com.google.gitiles.TestGitilesUrls.URLS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.storage.dfs.DfsRepository;
import org.eclipse.jgit.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.storage.dfs.InMemoryRepository;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/** Tests for {@link RepositoryIndexServlet}. */
public class RepositoryIndexServletTest extends TestCase {
  private TestRepository<DfsRepository> repo;
  private RepositoryIndexServlet servlet;

  @Override
  protected void setUp() throws Exception {
    repo = new TestRepository<DfsRepository>(
        new InMemoryRepository(new DfsRepositoryDescription("test")));
    servlet = new RepositoryIndexServlet(
        new DefaultRenderer(),
        new TestGitilesAccess(repo.getRepository()));
  }

  public void testEmpty() throws Exception {
    Map<String, ?> data = buildData();
    assertEquals(ImmutableList.of(), data.get("branches"));
    assertEquals(ImmutableList.of(), data.get("tags"));
  }

  public void testBranchesAndTags() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/bar").commit().create();
    repo.branch("refs/tags/baz").commit().create();
    repo.branch("refs/nope/quux").commit().create();
    Map<String, ?> data = buildData();

    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/bar", "bar"),
            ref("/b/test/+/foo", "foo")),
        data.get("branches"));
    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/baz", "baz")),
        data.get("tags"));
  }

  public void testAmbiguousBranch() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/bar").commit().create();
    repo.branch("refs/tags/foo").commit().create();
    Map<String, ?> data = buildData();

    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/bar", "bar"),
            ref("/b/test/+/refs/heads/foo", "foo")),
        data.get("branches"));
    assertEquals(
        ImmutableList.of(
            // refs/tags/ is searched before refs/heads/, so this does not
            // appear ambiguous.
            ref("/b/test/+/foo", "foo")),
        data.get("tags"));
  }

  public void testAmbiguousRelativeToNonBranchOrTag() throws Exception {
    repo.branch("refs/foo").commit().create();
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/tags/foo").commit().create();
    Map<String, ?> data = buildData();

    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/refs/heads/foo", "foo")),
        data.get("branches"));
    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/refs/tags/foo", "foo")),
        data.get("tags"));
  }

  public void testRefsHeads() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/refs/heads/foo").commit().create();
    Map<String, ?> data = buildData();

    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/foo", "foo"),
            ref("/b/test/+/refs/heads/refs/heads/foo", "refs/heads/foo")),
        data.get("branches"));
  }

  private Map<String, ?> buildData() throws IOException {
    HttpServletRequest req = FakeHttpServletRequest.newRequest(repo.getRepository());
    ViewFilter.setView(req, GitilesView.repositoryIndex()
        .setHostName(URLS.getHostName(req))
        .setServletPath(req.getServletPath())
        .setRepositoryName("test")
        .build());
    return servlet.buildData(req);
  }

  private Map<String, String> ref(String url, String name) {
    return ImmutableMap.of("url", url, "name", name);
  }
}
