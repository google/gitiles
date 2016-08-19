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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.BaseEncoding;
import com.google.gitiles.TreeJsonData.Tree;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.restricted.StringData;
import java.util.List;
import java.util.Map;
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

/** Tests for {@PathServlet}. */
@SuppressWarnings("unchecked")
@RunWith(JUnit4.class)
public class PathServletTest extends ServletTest {
  @Test
  public void rootTreeHtml() throws Exception {
    repo.branch("master").commit().add("foo", "contents").create();

    Map<String, ?> data = buildData("/repo/+/master/");
    assertThat(data).containsEntry("type", "TREE");
    List<Map<String, ?>> entries = getTreeEntries(data);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).get("name")).isEqualTo("foo");
  }

  @Test
  public void subTreeHtml() throws Exception {
    repo.branch("master")
        .commit()
        .add("foo/bar", "bar contents")
        .add("baz", "baz contents")
        .create();

    Map<String, ?> data = buildData("/repo/+/master/");
    assertThat(data).containsEntry("type", "TREE");
    List<Map<String, ?>> entries = getTreeEntries(data);
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).get("name")).isEqualTo("baz");
    assertThat(entries.get(1).get("name")).isEqualTo("foo/");

    data = buildData("/repo/+/master/foo");
    assertThat(data).containsEntry("type", "TREE");
    entries = getTreeEntries(data);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).get("name")).isEqualTo("bar");

    data = buildData("/repo/+/master/foo/");
    assertThat(data).containsEntry("type", "TREE");
    entries = getTreeEntries(data);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).get("name")).isEqualTo("bar");
  }

  @Test
  public void fileHtml() throws Exception {
    repo.branch("master").commit().add("foo", "foo\ncontents\n").create();

    Map<String, ?> data = buildData("/repo/+/master/foo");
    assertThat(data).containsEntry("type", "REGULAR_FILE");

    SoyListData lines = (SoyListData) getBlobData(data).get("lines");
    assertThat(lines.length()).isEqualTo(2);

    SoyListData spans = lines.getListData(0);
    assertThat(spans.length()).isEqualTo(1);
    assertThat(spans.getMapData(0).get("classes")).isEqualTo(StringData.forValue("pln"));
    assertThat(spans.getMapData(0).get("text")).isEqualTo(StringData.forValue("foo"));

    spans = lines.getListData(1);
    assertThat(spans.length()).isEqualTo(1);
    assertThat(spans.getMapData(0).get("classes")).isEqualTo(StringData.forValue("pln"));
    assertThat(spans.getMapData(0).get("text")).isEqualTo(StringData.forValue("contents"));
  }

  @Test
  public void symlinkHtml() throws Exception {
    final RevBlob link = repo.blob("foo");
    repo.branch("master")
        .commit()
        .add("foo", "contents")
        .edit(
            new PathEdit("bar") {
              @Override
              public void apply(DirCacheEntry ent) {
                ent.setFileMode(FileMode.SYMLINK);
                ent.setObjectId(link);
              }
            })
        .create();

    Map<String, ?> data = buildData("/repo/+/master/bar");
    assertThat(data).containsEntry("type", "SYMLINK");
    assertThat(getBlobData(data)).containsEntry("target", "foo");
  }

  @Test
  public void gitlinkHtml() throws Exception {
    String gitmodules =
        "[submodule \"gitiles\"]\n"
            + "  path = gitiles\n"
            + "  url = https://gerrit.googlesource.com/gitiles\n";
    final String gitilesSha = "2b2f34bba3c2be7e2506ce6b1f040949da350cf9";
    repo.branch("master")
        .commit()
        .add(".gitmodules", gitmodules)
        .edit(
            new PathEdit("gitiles") {
              @Override
              public void apply(DirCacheEntry ent) {
                ent.setFileMode(FileMode.GITLINK);
                ent.setObjectId(ObjectId.fromString(gitilesSha));
              }
            })
        .create();

    Map<String, ?> data = buildData("/repo/+/master/gitiles");
    assertThat(data).containsEntry("type", "GITLINK");

    Map<String, ?> linkData = getBlobData(data);
    assertThat(linkData).containsEntry("sha", gitilesSha);
    assertThat(linkData).containsEntry("remoteUrl", "https://gerrit.googlesource.com/gitiles");
    assertThat(linkData).containsEntry("httpUrl", "https://gerrit.googlesource.com/gitiles");
  }

  @Test
  public void blobText() throws Exception {
    repo.branch("master").commit().add("foo", "contents").create();
    String text = buildBlob("/repo/+/master/foo", "100644");
    assertThat(text).isEqualTo("contents");
  }

  @Test
  public void symlinkText() throws Exception {
    final RevBlob link = repo.blob("foo");
    repo.branch("master")
        .commit()
        .edit(
            new PathEdit("baz") {
              @Override
              public void apply(DirCacheEntry ent) {
                ent.setFileMode(FileMode.SYMLINK);
                ent.setObjectId(link);
              }
            })
        .create();
    String text = buildBlob("/repo/+/master/baz", "120000");
    assertThat(text).isEqualTo("foo");
  }

  @Test
  public void treeText() throws Exception {
    RevBlob blob = repo.blob("contents");
    RevTree tree = repo.tree(repo.file("foo/bar", blob));
    repo.branch("master").commit().setTopLevelTree(tree).create();

    String expected = "040000 tree " + repo.get(tree, "foo").name() + "\tfoo\n";
    assertThat(buildBlob("/repo/+/master/", "040000")).isEqualTo(expected);

    expected = "100644 blob " + blob.name() + "\tbar\n";
    assertThat(buildBlob("/repo/+/master/foo", "040000")).isEqualTo(expected);
    assertThat(buildBlob("/repo/+/master/foo/", "040000")).isEqualTo(expected);
  }

  @Test
  public void treeTextEscaped() throws Exception {
    RevBlob blob = repo.blob("contents");
    repo.branch("master").commit().add("foo\nbar\rbaz", blob).create();

    assertThat(buildBlob("/repo/+/master/", "040000"))
        .isEqualTo("100644 blob " + blob.name() + "\t\"foo\\nbar\\rbaz\"\n");
  }

  @Test
  public void nonBlobText() throws Exception {
    String gitmodules =
        "[submodule \"gitiles\"]\n"
            + "  path = gitiles\n"
            + "  url = https://gerrit.googlesource.com/gitiles\n";
    final String gitilesSha = "2b2f34bba3c2be7e2506ce6b1f040949da350cf9";
    repo.branch("master")
        .commit()
        .add("foo/bar", "contents")
        .add(".gitmodules", gitmodules)
        .edit(
            new PathEdit("gitiles") {
              @Override
              public void apply(DirCacheEntry ent) {
                ent.setFileMode(FileMode.GITLINK);
                ent.setObjectId(ObjectId.fromString(gitilesSha));
              }
            })
        .create();

    assertNotFound("/repo/+/master/nonexistent", "format=text");
    assertNotFound("/repo/+/master/gitiles", "format=text");
  }

  @Test
  public void treeJsonSizes() throws Exception {
    RevCommit c = repo.parseBody(repo.branch("master").commit().add("baz", "01234567").create());

    Tree tree = buildJson(Tree.class, "/repo/+/master/", "long=1");

    assertThat(tree.id).isEqualTo(c.getTree().name());
    assertThat(tree.entries).hasSize(1);
    assertThat(tree.entries.get(0).mode).isEqualTo(0100644);
    assertThat(tree.entries.get(0).type).isEqualTo("blob");
    assertThat(tree.entries.get(0).name).isEqualTo("baz");
    assertThat(tree.entries.get(0).size).isEqualTo(8);
  }

  @Test
  public void treeJsonLinkTarget() throws Exception {
    final ObjectId targetID = repo.blob("target");
    RevCommit c =
        repo.parseBody(
            repo.branch("master")
                .commit()
                .edit(
                    new PathEdit("link") {
                      @Override
                      public void apply(DirCacheEntry ent) {
                        ent.setFileMode(FileMode.SYMLINK);
                        ent.setObjectId(targetID);
                      }
                    })
                .create());

    Tree tree = buildJson(Tree.class, "/repo/+/master/", "long=1");

    assertThat(tree.id).isEqualTo(c.getTree().name());
    assertThat(tree.entries).hasSize(1);

    TreeJsonData.Entry e = tree.entries.get(0);
    assertThat(e.mode).isEqualTo(0120000);
    assertThat(e.type).isEqualTo("blob");
    assertThat(e.name).isEqualTo("link");
    assertThat(e.id).isEqualTo(targetID.name());
    assertThat(e.target).isEqualTo("target");
  }

  @Test
  public void treeJsonRecursive() throws Exception {
    RevCommit c =
        repo.parseBody(
            repo.branch("master")
                .commit()
                .add("foo/baz/bar/a", "bar contents")
                .add("foo/baz/bar/b", "bar contents")
                .add("baz", "baz contents")
                .create());
    Tree tree = buildJson(Tree.class, "/repo/+/master/", "recursive=1");

    assertThat(tree.id).isEqualTo(c.getTree().name());
    assertThat(tree.entries).hasSize(3);

    assertThat(tree.entries.get(0).name).isEqualTo("baz");
    assertThat(tree.entries.get(1).name).isEqualTo("foo/baz/bar/a");
    assertThat(tree.entries.get(2).name).isEqualTo("foo/baz/bar/b");

    tree = buildJson(Tree.class, "/repo/+/master/foo/baz", "recursive=1");

    assertThat(tree.entries).hasSize(2);

    assertThat(tree.entries.get(0).name).isEqualTo("bar/a");
    assertThat(tree.entries.get(1).name).isEqualTo("bar/b");
  }

  @Test
  public void treeJson() throws Exception {
    RevCommit c =
        repo.parseBody(
            repo.branch("master")
                .commit()
                .add("foo/bar", "bar contents")
                .add("baz", "baz contents")
                .create());

    Tree tree = buildJson(Tree.class, "/repo/+/master/");
    assertThat(tree.id).isEqualTo(c.getTree().name());
    assertThat(tree.entries).hasSize(2);
    assertThat(tree.entries.get(0).mode).isEqualTo(0100644);
    assertThat(tree.entries.get(0).type).isEqualTo("blob");
    assertThat(tree.entries.get(0).id).isEqualTo(repo.get(c.getTree(), "baz").name());
    assertThat(tree.entries.get(0).name).isEqualTo("baz");
    assertThat(tree.entries.get(1).mode).isEqualTo(040000);
    assertThat(tree.entries.get(1).type).isEqualTo("tree");
    assertThat(tree.entries.get(1).id).isEqualTo(repo.get(c.getTree(), "foo").name());
    assertThat(tree.entries.get(1).name).isEqualTo("foo");

    tree = buildJson(Tree.class, "/repo/+/master/foo");
    assertThat(tree.id).isEqualTo(repo.get(c.getTree(), "foo").name());
    assertThat(tree.entries).hasSize(1);
    assertThat(tree.entries.get(0).mode).isEqualTo(0100644);
    assertThat(tree.entries.get(0).type).isEqualTo("blob");
    assertThat(tree.entries.get(0).id).isEqualTo(repo.get(c.getTree(), "foo/bar").name());
    assertThat(tree.entries.get(0).name).isEqualTo("bar");

    tree = buildJson(Tree.class, "/repo/+/master/foo/");
    assertThat(tree.id).isEqualTo(repo.get(c.getTree(), "foo").name());
    assertThat(tree.entries).hasSize(1);
    assertThat(tree.entries.get(0).mode).isEqualTo(0100644);
    assertThat(tree.entries.get(0).type).isEqualTo("blob");
    assertThat(tree.entries.get(0).id).isEqualTo(repo.get(c.getTree(), "foo/bar").name());
    assertThat(tree.entries.get(0).name).isEqualTo("bar");
  }

  private Map<String, ?> getBlobData(Map<String, ?> data) {
    return ((Map<String, Map<String, ?>>) data).get("data");
  }

  private List<Map<String, ?>> getTreeEntries(Map<String, ?> data) {
    return ((Map<String, List<Map<String, ?>>>) data.get("data")).get("entries");
  }

  private String buildBlob(String path, String expectedMode) throws Exception {
    FakeHttpServletResponse res = buildText(path);
    assertThat(res.getHeader(PathServlet.MODE_HEADER)).isEqualTo(expectedMode);
    String base64 = res.getActualBodyString();
    return new String(BaseEncoding.base64().decode(base64), UTF_8);
  }
}
