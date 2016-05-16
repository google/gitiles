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

import com.google.gitiles.GitilesView;

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
    MarkdownToHtml md = new MarkdownToHtml(view, config);
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
    MarkdownToHtml md = new MarkdownToHtml(view, config);

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
        config);
  }

  private MarkdownToHtml repoIndexReadme() {
    return readme(view);
  }

  private MarkdownToHtml revisionReadme() {
    return readme(GitilesView.revision().copyFrom(view).build());
  }

  private MarkdownToHtml treeReadme(String path) {
    return readme(GitilesView.path().copyFrom(view).setPathPart(path).build());
  }

  private MarkdownToHtml readme(GitilesView v) {
    return new MarkdownToHtml(v, config).setReadme(true);
  }
}
