// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gitiles.doc;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.CharMatcher;
import com.google.gitiles.GitilesView;
import com.google.gitiles.RootedDocServlet;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LinkTest {
  private GitilesView view;
  private Config config;

  @Before
  public void setup() {
    view = GitilesView.revision()
        .setHostName("127.0.0.1")
        .setServletPath("/g")
        .setRepositoryName("repo")
        .setRevision("HEAD")
        .build();
    config = new Config();
  }

  @Test
  public void httpLink() {
    MarkdownToHtml md = new MarkdownToHtml(view, config, "index.md");
    String url;

    url = "http://example.com/foo.html";
    assertThat(md.href(url)).isEqualTo(url);

    url = "https://example.com/foo.html";
    assertThat(md.href(url)).isEqualTo(url);

    url = "//example.com/foo.html";
    assertThat(md.href(url)).isEqualTo(url);
  }

  @Test
  public void absolutePath() {
    MarkdownToHtml md = new MarkdownToHtml(view, config, "index.md");

    assertThat(md.href("/")).isEqualTo("/g/repo/+/HEAD/");
    assertThat(md.href("/index.md")).isEqualTo("/g/repo/+/HEAD/index.md");
    assertThat(md.href("/doc/index.md")).isEqualTo("/g/repo/+/HEAD/doc/index.md");

    // GitilesView trims trailing '/' from path expressions.
    assertThat(md.href("/doc/")).isEqualTo("/g/repo/+/HEAD/doc");
  }

  @Test
  public void relativePathInRootFile() {
    testMarkdownInRoot(file("/index.md"));
  }

  @Test
  public void relativePathInTreeFile() {
    testMarkdownInTree(file("/doc/index.md"));
  }

  @Test
  public void relativePathInRepositoryIndexReadme() {
    testMarkdownInRoot(repoIndexReadme());
  }

  @Test
  public void relativePathInCommitReadme() {
    testMarkdownInRoot(revisionReadme());
  }

  @Test
  public void relativePathInTreeReadme() {
    testMarkdownInTree(treeReadme("/doc"));
    testMarkdownInTree(treeReadme("/doc/"));
  }

  private static void testMarkdownInRoot(MarkdownToHtml md) {
    assertThat(md.href("#Help")).isEqualTo("#Help");
    assertThat(md.href("setup.md#Help"))
        .isEqualTo("/g/repo/+/HEAD/setup.md#Help");

    assertThat(md.href("setup.md")).isEqualTo("/g/repo/+/HEAD/setup.md");
    assertThat(md.href("./setup.md")).isEqualTo("/g/repo/+/HEAD/setup.md");
    assertThat(md.href("./")).isEqualTo("/g/repo/+/HEAD/");
    assertThat(md.href(".")).isEqualTo("/g/repo/+/HEAD/");

    assertThat(md.href("../")).isEqualTo("#zSoyz");
    assertThat(md.href("../../")).isEqualTo("#zSoyz");
    assertThat(md.href("../..")).isEqualTo("#zSoyz");
    assertThat(md.href("..")).isEqualTo("#zSoyz");
  }

  private static void testMarkdownInTree(MarkdownToHtml md) {
    assertThat(md.href("#Help")).isEqualTo("#Help");
    assertThat(md.href("setup.md#Help"))
        .isEqualTo("/g/repo/+/HEAD/doc/setup.md#Help");

    assertThat(md.href("setup.md")).isEqualTo("/g/repo/+/HEAD/doc/setup.md");
    assertThat(md.href("./setup.md")).isEqualTo("/g/repo/+/HEAD/doc/setup.md");
    assertThat(md.href("../setup.md")).isEqualTo("/g/repo/+/HEAD/setup.md");
    assertThat(md.href("../tech/setup.md")).isEqualTo("/g/repo/+/HEAD/tech/setup.md");

    assertThat(md.href("./")).isEqualTo("/g/repo/+/HEAD/doc");
    assertThat(md.href(".")).isEqualTo("/g/repo/+/HEAD/doc");
    assertThat(md.href("../")).isEqualTo("/g/repo/+/HEAD/");
    assertThat(md.href("..")).isEqualTo("/g/repo/+/HEAD/");

    assertThat(md.href("../../")).isEqualTo("#zSoyz");
    assertThat(md.href("../..")).isEqualTo("#zSoyz");
    assertThat(md.href("../../../")).isEqualTo("#zSoyz");
    assertThat(md.href("../../..")).isEqualTo("#zSoyz");
  }

  private MarkdownToHtml file(String path) {
    return new MarkdownToHtml(
        GitilesView.doc().copyFrom(view).setPathPart(path).build(),
        config,
        path);
  }

  private MarkdownToHtml repoIndexReadme() {
    return readme(view, "README.md");
  }

  private MarkdownToHtml revisionReadme() {
    return readme(GitilesView.revision().copyFrom(view).build(), "README.md");
  }

  private MarkdownToHtml treeReadme(String path) {
    GitilesView v = GitilesView.path().copyFrom(view).setPathPart(path).build();
    String file = CharMatcher.is('/').trimTrailingFrom(path) + "/README.md";
    return readme(v, file);
  }

  private MarkdownToHtml readme(GitilesView v, String path) {
    return new MarkdownToHtml(v, config, path);
  }

  @Test
  public void rootedDocInRoot() {
    testRootedDocInRoot(rootedDoc("/", "/index.md"));
    testRootedDocInRoot(rootedDoc("/index.md", "/index.md"));
  }

  private void testRootedDocInRoot(MarkdownToHtml md) {
    assertThat(md.href("setup.md")).isEqualTo("/setup.md");
    assertThat(md.href("./setup.md")).isEqualTo("/setup.md");
    assertThat(md.href("./")).isEqualTo("/");
    assertThat(md.href(".")).isEqualTo("/");

    assertThat(md.href("../")).isEqualTo("#zSoyz");
    assertThat(md.href("../../")).isEqualTo("#zSoyz");
    assertThat(md.href("../..")).isEqualTo("#zSoyz");
    assertThat(md.href("..")).isEqualTo("#zSoyz");
  }

  @Test
  public void rootedDocInTree() {
    testRootedDocInTree(rootedDoc("/doc", "/doc/index.md"));
    testRootedDocInTree(rootedDoc("/doc/", "/doc/index.md"));
    testRootedDocInTree(rootedDoc("/doc/index.md", "/doc/index.md"));
  }

  private void testRootedDocInTree(MarkdownToHtml md) {
    assertThat(md.href("setup.md")).isEqualTo("/doc/setup.md");
    assertThat(md.href("./setup.md")).isEqualTo("/doc/setup.md");
    assertThat(md.href("../setup.md")).isEqualTo("/setup.md");
    assertThat(md.href("../tech/setup.md")).isEqualTo("/tech/setup.md");

    assertThat(md.href("./")).isEqualTo("/doc");
    assertThat(md.href(".")).isEqualTo("/doc");
    assertThat(md.href("../")).isEqualTo("/");
    assertThat(md.href("..")).isEqualTo("/");

    assertThat(md.href("../../")).isEqualTo("#zSoyz");
    assertThat(md.href("../..")).isEqualTo("#zSoyz");
    assertThat(md.href("../../../")).isEqualTo("#zSoyz");
    assertThat(md.href("../../..")).isEqualTo("#zSoyz");
  }

  private MarkdownToHtml rootedDoc(String path, String file) {
    GitilesView view = GitilesView.rootedDoc()
        .setHostName("gerritcodereview.com")
        .setServletPath("")
        .setRevision(RootedDocServlet.BRANCH)
        .setPathPart(path)
        .build();
    return new MarkdownToHtml(view, config, file);
  }
}
