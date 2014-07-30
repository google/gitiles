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

import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.google.gitiles.RefServlet.RefJsonData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Tests for {@link Linkifier}. */
public class RefServletTest {
  private TestRepository<DfsRepository> repo;
  private GitilesServlet servlet;

  @Before
  public void setUp() throws Exception {
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

  @Test
  public void evilRefName() throws Exception {
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

  @Test
  public void getRefsTextAll() throws Exception {
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

  @Test
  public void getRefsTextAllTrailingSlash() throws Exception {
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

  @Test
  public void getRefsHeadsText() throws Exception {
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

  @Test
  public void getRefsHeadsTextTrailingSlash() throws Exception {
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

  @Test
  public void noHeadText() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+refs/HEAD");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    // /+refs/foo means refs/foo(/*), so this is empty.
    assertEquals("", res.getActualBodyString());
  }

  @Test
  public void singleHeadText() throws Exception {
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

  @Test
  public void singlePeeledTagText() throws Exception {
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

  @Test
  public void getRefsJsonAll() throws Exception {
    Map<String, RefJsonData> result = buildJson("/test/+refs");
    List<String> keys = ImmutableList.copyOf(result.keySet());
    assertEquals(ImmutableList.of(
          "HEAD",
          "refs/heads/branch",
          "refs/heads/master",
          "refs/tags/atag",
          "refs/tags/ctag"),
        keys);

    RefJsonData head = result.get(keys.get(0));
    assertEquals(id("HEAD"), head.value);
    assertNull(head.peeled);
    assertEquals("refs/heads/master", head.target);

    RefJsonData branch = result.get(keys.get(1));
    assertEquals(id("refs/heads/branch"), branch.value);
    assertNull(branch.peeled);
    assertNull(branch.target);

    RefJsonData master = result.get(keys.get(2));
    assertEquals(id("refs/heads/master"), master.value);
    assertNull(master.peeled);
    assertNull(master.target);

    RefJsonData atag = result.get(keys.get(3));
    assertEquals(id("refs/tags/atag"), atag.value);
    assertEquals(peeled("refs/tags/atag"), atag.peeled);
    assertNull(atag.target);

    RefJsonData ctag = result.get(keys.get(4));
    assertEquals(id("refs/tags/ctag"), ctag.value);
    assertNull(ctag.peeled);
    assertNull(ctag.target);
  }

  @Test
  public void getRefsHeadsJson() throws Exception {
    Map<String, RefJsonData> result = buildJson("/test/+refs/heads");
    List<String> keys = ImmutableList.copyOf(result.keySet());
    assertEquals(ImmutableList.of(
          "branch",
          "master"),
        keys);

    RefJsonData branch = result.get(keys.get(0));
    assertEquals(id("refs/heads/branch"), branch.value);
    assertNull(branch.peeled);
    assertNull(branch.target);

    RefJsonData master = result.get(keys.get(1));
    assertEquals(id("refs/heads/master"), master.value);
    assertNull(master.peeled);
    assertNull(master.target);
  }

  private Map<String, RefJsonData> buildJson(String path) throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo(path);
    req.setQueryString("format=JSON");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    assertEquals(200, res.getStatus());
    assertEquals("application/json", res.getHeader(HttpHeaders.CONTENT_TYPE));
    String body = res.getActualBodyString();
    String magic = ")]}'\n";
    assertEquals(magic, body.substring(0, magic.length()));
    return new Gson().fromJson(body.substring(magic.length()), new TypeToken<Map<String, RefJsonData>>() {}.getType());
  }
}
