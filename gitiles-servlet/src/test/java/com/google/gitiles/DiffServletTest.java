// Copyright (C) 2014 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles;

import static com.google.common.collect.Iterables.getOnlyElement;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class DiffServletTest {
  private TestRepository<DfsRepository> repo;
  private GitilesServlet servlet;

  @Before
  public void setUp() throws Exception {
    DfsRepository r = new InMemoryRepository(new DfsRepositoryDescription("test"));
    repo = new TestRepository<DfsRepository>(r);
    servlet = TestGitilesServlet.create(repo);
  }

  @Test
  public void diffFileOneParentHtml() throws Exception {
    String contents1 = "foo\n";
    String contents2 = "foo\ncontents\n";
    RevCommit c1 = repo.update("master", repo.commit().add("foo", contents1));
    RevCommit c2 = repo.update("master", repo.commit().parent(c1).add("foo", contents2));

    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+diff/" + c2.name() + "^!/foo");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    String diffHeader = String.format(
        "diff --git <a href=\"/b/test/+/%s/foo\">a/foo</a> <a href=\"/b/test/+/%s/foo\">b/foo</a>",
        c1.name(), c2.name());
    String actual = res.getActualBodyString();
    assertTrue(String.format("Expected diff body to contain [%s]:\n%s", diffHeader, actual),
        actual.indexOf(diffHeader) >= 0);
  }

  @Test
  public void diffFileNoParentsText() throws Exception {
    String contents = "foo\ncontents\n";
    RevCommit c = repo.update("master", repo.commit().add("foo", contents));

    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+diff/" + c.name() + "^!/foo");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    Patch p = parsePatch(res.getActualBody());
    FileHeader f = getOnlyElement(p.getFiles());
    assertEquals(ChangeType.ADD, f.getChangeType());
    assertEquals(DiffEntry.DEV_NULL, f.getPath(Side.OLD));
    assertEquals("foo", f.getPath(Side.NEW));

    RawText rt = new RawText(contents.getBytes(UTF_8));
    Edit e = getOnlyElement(getOnlyElement(f.getHunks()).toEditList());
    assertEquals(Type.INSERT, e.getType());
    assertEquals(contents, rt.getString(e.getBeginB(), e.getEndB(), false));
  }

  @Test
  public void diffFileOneParentText() throws Exception {
    String contents1 = "foo\n";
    String contents2 = "foo\ncontents\n";
    RevCommit c1 = repo.update("master", repo.commit().add("foo", contents1));
    RevCommit c2 = repo.update("master", repo.commit().parent(c1).add("foo", contents2));

    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+diff/" + c2.name() + "^!/foo");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    Patch p = parsePatch(res.getActualBody());
    FileHeader f = getOnlyElement(p.getFiles());
    assertEquals(ChangeType.MODIFY, f.getChangeType());
    assertEquals("foo", f.getPath(Side.OLD));
    assertEquals("foo", f.getPath(Side.NEW));

    RawText rt2 = new RawText(contents2.getBytes(UTF_8));
    Edit e = getOnlyElement(getOnlyElement(f.getHunks()).toEditList());
    assertEquals(Type.INSERT, e.getType());
    assertEquals("contents\n", rt2.getString(e.getBeginB(), e.getEndB(), false));
  }

  @Test
  public void diffDirectoryText() throws Exception {
    String contents = "contents\n";
    RevCommit c = repo.update("master", repo.commit()
        .add("dir/foo", contents)
        .add("dir/bar", contents)
        .add("baz", contents));

    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/test/+diff/" + c.name() + "^!/dir");
    req.setQueryString("format=TEXT");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);

    Patch p = parsePatch(res.getActualBody());
    assertEquals(2, p.getFiles().size());
    assertEquals("dir/bar", p.getFiles().get(0).getPath(Side.NEW));
    assertEquals("dir/foo", p.getFiles().get(1).getPath(Side.NEW));
  }

  private static Patch parsePatch(byte[] enc) {
    byte[] buf = BaseEncoding.base64().decode(new String(enc, UTF_8));
    Patch p = new Patch();
    p.parse(buf, 0, buf.length);
    assertEquals(ImmutableList.of(), p.getErrors());
    return p;
  }
}
