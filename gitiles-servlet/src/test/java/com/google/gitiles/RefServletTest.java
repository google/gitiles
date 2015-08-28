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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gitiles.RefServlet.RefJsonData;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/** Tests for {@link RefServlet}. */
@RunWith(JUnit4.class)
public class RefServletTest extends ServletTest {
  private void setUpSimpleRefs() throws Exception {
    RevCommit commit = repo.branch("refs/heads/master").commit().create();
    repo.update("refs/heads/branch", commit);
    repo.update("refs/tags/ctag", commit);
    RevTag tag = repo.tag("atag", commit);
    repo.update("refs/tags/atag", tag);
    repo.getRepository().updateRef("HEAD").link("refs/heads/master");
  }

  @Test
  public void evilRefName() throws Exception {
    setUpSimpleRefs();
    String evilRefName = "refs/evil/<script>window.close();</script>/&foo";
    assertTrue(Repository.isValidRefName(evilRefName));
    repo.branch(evilRefName).commit().create();

    FakeHttpServletResponse res = buildText("/repo/+refs/evil");
    assertEquals(
        id(evilRefName) + " refs/evil/&lt;script&gt;window.close();&lt;/script&gt;/&amp;foo\n",
        res.getActualBodyString());
  }

  @Test
  public void getRefsTextAll() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs");

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
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/");

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
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/heads");

    assertEquals(
        id("refs/heads/branch") + " refs/heads/branch\n"
        + id("refs/heads/master") + " refs/heads/master\n",
        res.getActualBodyString());
  }

  @Test
  public void getRefsHeadsTextTrailingSlash() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/heads/");

    assertEquals(
        id("refs/heads/branch") + " refs/heads/branch\n"
        + id("refs/heads/master") + " refs/heads/master\n",
        res.getActualBodyString());
  }

  @Test
  public void noHeadText() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/HEAD");

    // /+refs/foo means refs/foo(/*), so this is empty.
    assertEquals("", res.getActualBodyString());
  }

  @Test
  public void singleHeadText() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/heads/master");

    assertEquals(
        id("refs/heads/master") + " refs/heads/master\n",
        res.getActualBodyString());
  }

  @Test
  public void singlePeeledTagText() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/tags/atag");

    assertEquals(
        id("refs/tags/atag") + " refs/tags/atag\n"
        + peeled("refs/tags/atag") + " refs/tags/atag^{}\n",
        res.getActualBodyString());
  }

  @Test
  public void getRefsJsonAll() throws Exception {
    setUpSimpleRefs();
    Map<String, RefJsonData> result = buildRefJson("/repo/+refs");
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
    setUpSimpleRefs();
    Map<String, RefJsonData> result = buildRefJson("/repo/+refs/heads");
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

  private Map<String, RefJsonData> buildRefJson(String path) throws Exception {
    return buildJson(
        path,
        new TypeToken<Map<String, RefJsonData>>() {}.getType());
  }

  @Test
  public void emptySoy() throws Exception {
    assertEquals(ImmutableList.of(), buildBranchesSoyData());
    assertEquals(ImmutableList.of(), buildTagsSoyData());
  }

  @Test
  public void branchesAndTagsSoy() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/bar").commit().create();
    repo.branch("refs/tags/baz").commit().create();
    repo.branch("refs/nope/quux").commit().create();

    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/bar", "bar"),
            ref("/b/test/+/foo", "foo")),
        buildBranchesSoyData());
    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/baz", "baz")),
        buildTagsSoyData());
  }

  @Test
  public void ambiguousBranchSoy() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/bar").commit().create();
    repo.branch("refs/tags/foo").commit().create();

    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/bar", "bar"),
            ref("/b/test/+/refs/heads/foo", "foo")),
        buildBranchesSoyData());
    assertEquals(
        ImmutableList.of(
            // refs/tags/ is searched before refs/heads/, so this does not
            // appear ambiguous.
            ref("/b/test/+/foo", "foo")),
        buildTagsSoyData());
  }

  @Test
  public void ambiguousRelativeToNonBranchOrTagSoy() throws Exception {
    repo.branch("refs/foo").commit().create();
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/tags/foo").commit().create();

    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/refs/heads/foo", "foo")),
        buildBranchesSoyData());
    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/refs/tags/foo", "foo")),
        buildTagsSoyData());
  }

  @Test
  public void refsHeadsSoy() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/refs/heads/foo").commit().create();

    assertEquals(
        ImmutableList.of(
            ref("/b/test/+/foo", "foo"),
            ref("/b/test/+/refs/heads/refs/heads/foo", "refs/heads/foo")),
        buildBranchesSoyData());
  }

  private HttpServletRequest buildSoyRequest() {
    HttpServletRequest req = FakeHttpServletRequest.newRequest(repo.getRepository());
    ViewFilter.setView(req, GitilesView.repositoryIndex()
        .setHostName(URLS.getHostName(req))
        .setServletPath(req.getServletPath())
        .setRepositoryName("test")
        .build());
    return req;
  }

  private List<?> buildBranchesSoyData() throws Exception {
    return RefServlet.getBranchesSoyData(buildSoyRequest(), Integer.MAX_VALUE);
  }

  private List<?> buildTagsSoyData() throws Exception {
    try (RevWalk rw = new RevWalk(repo.getRepository())) {
      return RefServlet.getTagsSoyData(buildSoyRequest(),
          new TimeCache(TimeCache.defaultBuilder()), rw, Integer.MAX_VALUE);
    }
  }

  private String id(String refName) throws IOException {
    return ObjectId.toString(repo.getRepository().getRef(refName).getObjectId());
  }

  private String peeled(String refName) throws IOException {
    return ObjectId.toString(repo.getRepository().peel(
          repo.getRepository().getRef(refName)).getPeeledObjectId());
  }

  private Map<String, String> ref(String url, String name) {
    return ImmutableMap.of("url", url, "name", name);
  }
}
