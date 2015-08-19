// Copyright (C) 2014 Google Inc. All Rights Reserved.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import com.google.common.io.BaseEncoding;
import com.google.gitiles.TreeJsonData.Tree;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.restricted.StringData;

import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Map;

/** Tests for {@PathServlet}. */
@SuppressWarnings("unchecked")
@RunWith(JUnit4.class)
public class PathServletTest extends ServletTest {
  @Test
  public void rootTreeHtml() throws Exception {
    repo.branch("master").commit().add("foo", "contents").create();

    Map<String, ?> data = buildData("/repo/+/master/");
    assertEquals("TREE", data.get("type"));
    List<Map<String, ?>> entries = getTreeEntries(data);
    assertEquals(1, entries.size());
    assertEquals("foo", entries.get(0).get("name"));
  }

  @Test
  public void subTreeHtml() throws Exception {
    repo.branch("master").commit()
        .add("foo/bar", "bar contents")
        .add("baz", "baz contents")
        .create();

    Map<String, ?> data = buildData("/repo/+/master/");
    assertEquals("TREE", data.get("type"));
    List<Map<String, ?>> entries = getTreeEntries(data);
    assertEquals(2, entries.size());
    assertEquals("baz", entries.get(0).get("name"));
    assertEquals("foo/", entries.get(1).get("name"));

    data = buildData("/repo/+/master/foo");
    assertEquals("TREE", data.get("type"));
    entries = getTreeEntries(data);
    assertEquals(1, entries.size());
    assertEquals("bar", entries.get(0).get("name"));

    data = buildData("/repo/+/master/foo/");
    assertEquals("TREE", data.get("type"));
    entries = getTreeEntries(data);
    assertEquals(1, entries.size());
    assertEquals("bar", entries.get(0).get("name"));
  }

  @Test
  public void fileHtml() throws Exception {
    repo.branch("master").commit().add("foo", "foo\ncontents\n").create();

    Map<String, ?> data = buildData("/repo/+/master/foo");
    assertEquals("REGULAR_FILE", data.get("type"));

    SoyListData lines = (SoyListData) getBlobData(data).get("lines");
    assertEquals(2, lines.length());

    SoyListData spans = lines.getListData(0);
    assertEquals(1, spans.length());
    assertEquals(StringData.forValue("pln"), spans.getMapData(0).get("classes"));
    assertEquals(StringData.forValue("foo"), spans.getMapData(0).get("text"));

    spans = lines.getListData(1);
    assertEquals(1, spans.length());
    assertEquals(StringData.forValue("pln"), spans.getMapData(0).get("classes"));
    assertEquals(StringData.forValue("contents"), spans.getMapData(0).get("text"));
  }

  @Test
  public void symlinkHtml() throws Exception {
    final RevBlob link = repo.blob("foo");
    repo.branch("master").commit().add("foo", "contents")
      .edit(new PathEdit("bar") {
        @Override
        public void apply(DirCacheEntry ent) {
          ent.setFileMode(FileMode.SYMLINK);
          ent.setObjectId(link);
        }
      }).create();

    Map<String, ?> data = buildData("/repo/+/master/bar");
    assertEquals("SYMLINK", data.get("type"));
    assertEquals("foo", getBlobData(data).get("target"));
  }

  @Test
  public void gitlinkHtml() throws Exception {
    String gitmodules = "[submodule \"gitiles\"]\n"
      + "  path = gitiles\n"
      + "  url = https://gerrit.googlesource.com/gitiles\n";
    final String gitilesSha = "2b2f34bba3c2be7e2506ce6b1f040949da350cf9";
    repo.branch("master").commit().add(".gitmodules", gitmodules)
        .edit(new PathEdit("gitiles") {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.GITLINK);
            ent.setObjectId(ObjectId.fromString(gitilesSha));
          }
        }).create();

    Map<String, ?> data = buildData("/repo/+/master/gitiles");
    assertEquals("GITLINK", data.get("type"));

    Map<String, ?> linkData = getBlobData(data);
    assertEquals(gitilesSha, linkData.get("sha"));
    assertEquals("https://gerrit.googlesource.com/gitiles", linkData.get("remoteUrl"));
    assertEquals("https://gerrit.googlesource.com/gitiles", linkData.get("httpUrl"));
  }

  @Test
  public void blobText() throws Exception {
    repo.branch("master").commit().add("foo", "contents").create();
    String text = buildBlob("/repo/+/master/foo", "100644");
    assertEquals("contents", text);
  }

  @Test
  public void symlinkText() throws Exception {
    final RevBlob link = repo.blob("foo");
    repo.branch("master").commit()
        .edit(new PathEdit("baz") {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.SYMLINK);
            ent.setObjectId(link);
          }
        }).create();
    String text = buildBlob("/repo/+/master/baz", "120000");
    assertEquals("foo", text);
  }

  @Test
  public void treeText() throws Exception {
    RevBlob blob = repo.blob("contents");
    RevTree tree = repo.tree(repo.file("foo/bar", blob));
    repo.branch("master").commit().setTopLevelTree(tree).create();

    String expected = "040000 tree " + repo.get(tree, "foo").name() + "\tfoo\n";
    assertEquals(expected, buildBlob("/repo/+/master/", "040000"));

    expected = "100644 blob " + blob.name() + "\tbar\n";
    assertEquals(expected, buildBlob("/repo/+/master/foo", "040000"));
    assertEquals(expected, buildBlob("/repo/+/master/foo/", "040000"));
  }

  @Test
  public void treeTextEscaped() throws Exception {
    RevBlob blob = repo.blob("contents");
    repo.branch("master").commit().add("foo\nbar\rbaz", blob).create();

    assertEquals("100644 blob " + blob.name() + "\t\"foo\\nbar\\rbaz\"\n",
        buildBlob("/repo/+/master/", "040000"));
  }

  @Test
  public void nonBlobText() throws Exception {
    String gitmodules = "[submodule \"gitiles\"]\n"
      + "  path = gitiles\n"
      + "  url = https://gerrit.googlesource.com/gitiles\n";
    final String gitilesSha = "2b2f34bba3c2be7e2506ce6b1f040949da350cf9";
    repo.branch("master").commit()
        .add("foo/bar", "contents")
        .add(".gitmodules", gitmodules)
        .edit(new PathEdit("gitiles") {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.GITLINK);
            ent.setObjectId(ObjectId.fromString(gitilesSha));
          }
        }).create();

    assertNotFound("/repo/+/master/nonexistent", "format=text");
    assertNotFound("/repo/+/master/gitiles", "format=text");
  }

  @Test
  public void treeJson() throws Exception {
    RevCommit c = repo.parseBody(repo.branch("master").commit()
        .add("foo/bar", "bar contents")
        .add("baz", "baz contents")
        .create());

    Tree tree = buildJson("/repo/+/master/", Tree.class);
    assertEquals(c.getTree().name(), tree.id);
    assertEquals(2, tree.entries.size());
    assertEquals(0100644, tree.entries.get(0).mode);
    assertEquals("blob", tree.entries.get(0).type);
    assertEquals(repo.get(c.getTree(), "baz").name(), tree.entries.get(0).id);
    assertEquals("baz", tree.entries.get(0).name);
    assertEquals(040000, tree.entries.get(1).mode);
    assertEquals("tree", tree.entries.get(1).type);
    assertEquals(repo.get(c.getTree(), "foo").name(), tree.entries.get(1).id);
    assertEquals("foo", tree.entries.get(1).name);

    tree = buildJson("/repo/+/master/foo", Tree.class);
    assertEquals(repo.get(c.getTree(), "foo").name(), tree.id);
    assertEquals(1, tree.entries.size());
    assertEquals(0100644, tree.entries.get(0).mode);
    assertEquals("blob", tree.entries.get(0).type);
    assertEquals(repo.get(c.getTree(), "foo/bar").name(), tree.entries.get(0).id);
    assertEquals("bar", tree.entries.get(0).name);

    tree = buildJson("/repo/+/master/foo/", Tree.class);
    assertEquals(repo.get(c.getTree(), "foo").name(), tree.id);
    assertEquals(1, tree.entries.size());
    assertEquals(0100644, tree.entries.get(0).mode);
    assertEquals("blob", tree.entries.get(0).type);
    assertEquals(repo.get(c.getTree(), "foo/bar").name(), tree.entries.get(0).id);
    assertEquals("bar", tree.entries.get(0).name);
  }

  private Map<String, ?> getBlobData(Map<String, ?> data) {
    return ((Map<String, Map<String, ?>>) data).get("data");
  }

  private List<Map<String, ?>> getTreeEntries(Map<String, ?> data) {
    return ((Map<String, List<Map<String, ?>>>) data.get("data")).get("entries");
  }

  private String buildBlob(String path, String expectedMode) throws Exception {
    FakeHttpServletResponse res = buildText(path);
    assertEquals(expectedMode, res.getHeader(PathServlet.MODE_HEADER));
    String base64 = res.getActualBodyString();
    return new String(BaseEncoding.base64().decode(base64), UTF_8);
  }
}
