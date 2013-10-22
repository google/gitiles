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

import java.io.IOException;

import junit.framework.TestCase;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;

/** Tests for {@link Linkifier}. */
public class RefServletTest extends TestCase {
  private TestRepository<DfsRepository> repo;
  private GitilesServlet servlet;

  @Override
  protected void setUp() throws Exception {
    DfsRepository r = new InMemoryRepository(new DfsRepositoryDescription("test"));
    repo = new TestRepository<DfsRepository>(r);

    RevCommit commit = repo.branch("refs/heads/master").commit().create();
    repo.update("refs/heads/branch", commit);
    repo.update("refs/tags/ctag", commit);
    RevTag tag = repo.tag("atag", commit);
    repo.update("refs/tags/atag", tag);
    r.updateRef("HEAD").link("refs/heads/master");

    servlet = TestGitilesServlet.create(repo);
  }

  private String id(String refName) throws IOException {
    return ObjectId.toString(repo.getRepository().getRef(refName).getObjectId());
  }

  private String peeled(String refName) throws IOException {
    return ObjectId.toString(repo.getRepository().peel(
          repo.getRepository().getRef(refName)).getPeeledObjectId());
  }

  public void testEvilRefName() throws Exception {
    String evilRefName = "refs/evil/<script>window.close();</script>/&foo";
    assertTrue(Repository.isValidRefName(evilRefName));
    repo.branch(evilRefName).commit().create();

    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs/evil");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(
        id(evilRefName) + " refs/evil/&lt;script&gt;window.close();&lt;/script&gt;/&amp;foo\n",
        res.getActualBodyString());
  }

  public void testGetRefsTextAll() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    assertEquals(
        id("HEAD") + " HEAD\n"
        + id("refs/heads/branch") + " refs/heads/branch\n"
        + id("refs/heads/master") + " refs/heads/master\n"
        + id("refs/tags/atag") + " refs/tags/atag\n"
        + peeled("refs/tags/atag") + " refs/tags/atag^{}\n"
        + id("refs/tags/ctag") + " refs/tags/ctag\n",
        res.getActualBodyString());
  }

  public void testGetRefsTextAllTrailingSlash() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    assertEquals(
        id("HEAD") + " HEAD\n"
        + id("refs/heads/branch") + " refs/heads/branch\n"
        + id("refs/heads/master") + " refs/heads/master\n"
        + id("refs/tags/atag") + " refs/tags/atag\n"
        + peeled("refs/tags/atag") + " refs/tags/atag^{}\n"
        + id("refs/tags/ctag") + " refs/tags/ctag\n",
        res.getActualBodyString());
  }

  public void testGetRefsHeadsText() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs/heads");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    assertEquals(
        id("refs/heads/branch") + " refs/heads/branch\n"
        + id("refs/heads/master") + " refs/heads/master\n",
        res.getActualBodyString());
  }

  public void testGetRefsHeadsTextTrailingSlash() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs/heads/");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    assertEquals(
        id("refs/heads/branch") + " refs/heads/branch\n"
        + id("refs/heads/master") + " refs/heads/master\n",
        res.getActualBodyString());
  }

  public void testNoHeadText() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs/HEAD");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    // /+refs/foo means refs/foo(/*), so this is empty.
    assertEquals("", res.getActualBodyString());
  }

  public void testSingleHeadText() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs/heads/master");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    assertEquals(
        id("refs/heads/master") + " refs/heads/master\n",
        res.getActualBodyString());
  }

  public void testSinglePeeledTagText() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs/tags/atag");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    assertEquals(
        id("refs/tags/atag") + " refs/tags/atag\n"
        + peeled("refs/tags/atag") + " refs/tags/atag^{}\n",
        res.getActualBodyString());
  }
}
