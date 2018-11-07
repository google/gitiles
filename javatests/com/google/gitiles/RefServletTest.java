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
import static com.google.gitiles.TestGitilesUrls.URLS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gitiles.RefServlet.RefJsonData;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    assertThat(Repository.isValidRefName(evilRefName)).isTrue();
    repo.branch(evilRefName).commit().create();

    FakeHttpServletResponse res = buildText("/repo/+refs/evil");
    assertThat(res.getActualBodyString())
        .isEqualTo(
            id(evilRefName) + " refs/evil/&lt;script&gt;window.close();&lt;/script&gt;/&amp;foo\n");
  }

  @Test
  public void getRefsTextAll() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs");

    assertThat(res.getActualBodyString())
        .isEqualTo(
            id("HEAD")
                + " HEAD\n"
                + id("refs/heads/branch")
                + " refs/heads/branch\n"
                + id("refs/heads/master")
                + " refs/heads/master\n"
                + id("refs/tags/atag")
                + " refs/tags/atag\n"
                + peeled("refs/tags/atag")
                + " refs/tags/atag^{}\n"
                + id("refs/tags/ctag")
                + " refs/tags/ctag\n");
  }

  @Test
  public void getRefsTextAllTrailingSlash() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/");

    assertThat(res.getActualBodyString())
        .isEqualTo(
            id("HEAD")
                + " HEAD\n"
                + id("refs/heads/branch")
                + " refs/heads/branch\n"
                + id("refs/heads/master")
                + " refs/heads/master\n"
                + id("refs/tags/atag")
                + " refs/tags/atag\n"
                + peeled("refs/tags/atag")
                + " refs/tags/atag^{}\n"
                + id("refs/tags/ctag")
                + " refs/tags/ctag\n");
  }

  @Test
  public void getRefsHeadsText() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/heads");

    assertThat(res.getActualBodyString())
        .isEqualTo(
            id("refs/heads/branch")
                + " refs/heads/branch\n"
                + id("refs/heads/master")
                + " refs/heads/master\n");
  }

  @Test
  public void getRefsHeadsTextTrailingSlash() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/heads/");

    assertThat(res.getActualBodyString())
        .isEqualTo(
            id("refs/heads/branch")
                + " refs/heads/branch\n"
                + id("refs/heads/master")
                + " refs/heads/master\n");
  }

  @Test
  public void noHeadText() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/HEAD");

    // /+refs/foo means refs/foo(/*), so this is empty.
    assertThat(res.getActualBodyString()).isEqualTo("");
  }

  @Test
  public void singleHeadText() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/heads/master");

    assertThat(res.getActualBodyString())
        .isEqualTo(id("refs/heads/master") + " refs/heads/master\n");
  }

  @Test
  public void singleHeadJson() throws Exception {
    setUpSimpleRefs();
    Map<String, RefJsonData> result = buildRefJson("/repo/+refs/heads/master");

    assertThat(result.keySet()).containsExactly("refs/heads/master");
    RefJsonData master = result.get("refs/heads/master");
    assertThat(master.value).isEqualTo(id("refs/heads/master"));
    assertThat(master.peeled).isNull();
    assertThat(master.target).isNull();
  }

  @Test
  public void singlePeeledTagText() throws Exception {
    setUpSimpleRefs();
    FakeHttpServletResponse res = buildText("/repo/+refs/tags/atag");

    assertThat(res.getActualBodyString())
        .isEqualTo(
            id("refs/tags/atag")
                + " refs/tags/atag\n"
                + peeled("refs/tags/atag")
                + " refs/tags/atag^{}\n");
  }

  @Test
  public void getRefsJsonAll() throws Exception {
    setUpSimpleRefs();
    Map<String, RefJsonData> result = buildRefJson("/repo/+refs");
    List<String> keys = ImmutableList.copyOf(result.keySet());
    assertThat(keys)
        .containsExactly(
            "HEAD", "refs/heads/branch", "refs/heads/master", "refs/tags/atag", "refs/tags/ctag")
        .inOrder();

    RefJsonData head = result.get(keys.get(0));
    assertThat(head.value).isEqualTo(id("HEAD"));
    assertThat(head.peeled).isNull();
    assertThat(head.target).isEqualTo("refs/heads/master");

    RefJsonData branch = result.get(keys.get(1));
    assertThat(branch.value).isEqualTo(id("refs/heads/branch"));
    assertThat(branch.peeled).isNull();
    assertThat(branch.target).isNull();

    RefJsonData master = result.get(keys.get(2));
    assertThat(master.value).isEqualTo(id("refs/heads/master"));
    assertThat(master.peeled).isNull();
    assertThat(master.target).isNull();

    RefJsonData atag = result.get(keys.get(3));
    assertThat(atag.value).isEqualTo(id("refs/tags/atag"));
    assertThat(atag.peeled).isEqualTo(peeled("refs/tags/atag"));
    assertThat(atag.target).isNull();

    RefJsonData ctag = result.get(keys.get(4));
    assertThat(ctag.value).isEqualTo(id("refs/tags/ctag"));
    assertThat(ctag.peeled).isNull();
    assertThat(ctag.target).isNull();
  }

  @Test
  public void getRefsHeadsJson() throws Exception {
    setUpSimpleRefs();
    Map<String, RefJsonData> result = buildRefJson("/repo/+refs/heads");
    List<String> keys = ImmutableList.copyOf(result.keySet());
    assertThat(keys).containsExactly("branch", "master").inOrder();

    RefJsonData branch = result.get(keys.get(0));
    assertThat(branch.value).isEqualTo(id("refs/heads/branch"));
    assertThat(branch.peeled).isNull();
    assertThat(branch.target).isNull();

    RefJsonData master = result.get(keys.get(1));
    assertThat(master.value).isEqualTo(id("refs/heads/master"));
    assertThat(master.peeled).isNull();
    assertThat(master.target).isNull();
  }

  private Map<String, RefJsonData> buildRefJson(String path) throws Exception {
    return buildJson(new TypeToken<Map<String, RefJsonData>>() {}, path);
  }

  @Test
  public void emptySoy() throws Exception {
    assertThat(buildBranchesSoyData()).isEmpty();
    assertThat(buildTagsSoyData()).isEmpty();
  }

  @Test
  public void branchesAndTagsSoy() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/bar").commit().create();
    repo.branch("refs/tags/baz").commit().create();
    repo.branch("refs/nope/quux").commit().create();

    assertThat(buildBranchesSoyData())
        .containsExactly(ref("/b/test/+/bar", "bar"), ref("/b/test/+/foo", "foo"))
        .inOrder();
    assertThat(buildTagsSoyData()).containsExactly(ref("/b/test/+/baz", "baz")).inOrder();
  }

  @Test
  public void ambiguousBranchSoy() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/bar").commit().create();
    repo.branch("refs/tags/foo").commit().create();

    assertThat(buildBranchesSoyData())
        .containsExactly(ref("/b/test/+/bar", "bar"), ref("/b/test/+/refs/heads/foo", "foo"))
        .inOrder();
    assertThat(buildTagsSoyData())
        .containsExactly(
            // refs/tags/ is searched before refs/heads/, so this does not
            // appear ambiguous.
            ref("/b/test/+/foo", "foo"))
        .inOrder();
  }

  @Test
  public void ambiguousRelativeToNonBranchOrTagSoy() throws Exception {
    repo.branch("refs/foo").commit().create();
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/tags/foo").commit().create();

    assertThat(buildBranchesSoyData())
        .containsExactly(ref("/b/test/+/refs/heads/foo", "foo"))
        .inOrder();
    assertThat(buildTagsSoyData()).containsExactly(ref("/b/test/+/refs/tags/foo", "foo")).inOrder();
  }

  @Test
  public void refsHeadsSoy() throws Exception {
    repo.branch("refs/heads/foo").commit().create();
    repo.branch("refs/heads/refs/heads/foo").commit().create();

    assertThat(buildBranchesSoyData())
        .containsExactly(
            ref("/b/test/+/foo", "foo"),
            ref("/b/test/+/refs/heads/refs/heads/foo", "refs/heads/foo"))
        .inOrder();
  }

  private HttpServletRequest buildSoyRequest() {
    HttpServletRequest req = FakeHttpServletRequest.newRequest(repo.getRepository());
    ViewFilter.setView(
        req,
        GitilesView.repositoryIndex()
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
      return RefServlet.getTagsSoyData(
          buildSoyRequest(), new TimeCache(TimeCache.defaultBuilder()), rw, Integer.MAX_VALUE);
    }
  }

  private String id(String refName) throws IOException {
    return ObjectId.toString(repo.getRepository().exactRef(refName).getObjectId());
  }

  private String peeled(String refName) throws IOException {
    return ObjectId.toString(
        repo.getRepository()
            .getRefDatabase()
            .peel(repo.getRepository().exactRef(refName))
            .getPeeledObjectId());
  }

  private Map<String, String> ref(String url, String name) {
    return ImmutableMap.of("url", url, "name", name);
  }
}
