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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.gitiles.GitilesView.Type;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for Gitiles views. */
@RunWith(JUnit4.class)
public class GitilesViewTest {
  private static final GitilesView HOST = GitilesView.hostIndex()
      .setServletPath("/b")
      .setHostName("host")
      .build();

  @Test
  public void emptyServletPath() throws Exception {
    GitilesView view = GitilesView.hostIndex()
        .setServletPath("")
        .setHostName("host")
        .build();
    assertThat(view.getServletPath()).isEqualTo("");
    assertThat(view.getType()).isEqualTo(Type.HOST_INDEX);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isNull();
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/?format=HTML");
    assertThat(view.getBreadcrumbs())
        .containsExactly(breadcrumb("host", "/?format=HTML"));
  }

  @Test
  public void hostIndex() throws Exception {
    assertThat(HOST.getServletPath()).isEqualTo("/b");
    assertThat(HOST.getType()).isEqualTo(Type.HOST_INDEX);
    assertThat(HOST.getHostName()).isEqualTo("host");
    assertThat(HOST.getRepositoryName()).isNull();
    assertThat(HOST.getRevision()).isEqualTo(Revision.NULL);
    assertThat(HOST.getPathPart()).isNull();
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(HOST.toUrl()).isEqualTo("/b/?format=HTML");
    assertThat(HOST.getBreadcrumbs())
        .containsExactly(breadcrumb("host", "/b/?format=HTML"));
  }

  @Test
  public void hostIndexOneComponentPrefix() throws Exception {
    GitilesView view = GitilesView.hostIndex()
        .copyFrom(HOST)
        .setRepositoryPrefix("foo")
        .build();

    assertThat(view.toUrl()).isEqualTo("/b/foo/");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
          breadcrumb("host", "/b/?format=HTML"),
          breadcrumb("foo", "/b/foo/"))
        .inOrder();
  }

  @Test
  public void hostIndexTwoComponentPrefix() throws Exception {
    GitilesView view = GitilesView.hostIndex()
        .copyFrom(HOST)
        .setRepositoryPrefix("foo/bar")
        .build();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"))
        .inOrder();
  }

  @Test
  public void queryParams() throws Exception {
    GitilesView view = GitilesView.log().copyFrom(HOST)
        .setRepositoryName("repo")
        .setRevision(Revision.named("master"))
        .putParam("foo", "foovalue")
        .putParam("bar", "barvalue")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("repo");
    assertThat(view.getRevision()).isEqualTo(Revision.named("master"));
    assertThat(view.getPathPart()).isNull();
    assertThat(view.getParameters()).containsExactly(
        ImmutableListMultimap.of(
            "foo", "foovalue",
            "bar", "barvalue"));

    assertThat(view.toUrl()).isEqualTo("/b/repo/+log/master?foo=foovalue&bar=barvalue");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("repo", "/b/repo/"),
            breadcrumb("master", "/b/repo/+log/master?foo=foovalue&bar=barvalue"))
        .inOrder();
  }

  @Test
  public void queryParamsCopiedOnlyOnSameType() throws Exception {
    GitilesView view = GitilesView.repositoryIndex().copyFrom(HOST)
        .setRepositoryName("repo")
        .putParam("foo", "foovalue")
        .putParam("bar", "barvalue")
        .build();
    assertThat(view.getParameters()).isNotEmpty();
    assertThat(GitilesView.repositoryIndex().copyFrom(view).build().getParameters())
        .isEqualTo(view.getParameters());
    assertThat(GitilesView.hostIndex().copyFrom(view).build().getParameters()).isEmpty();
  }

  @Test
  public void repositoryIndex() throws Exception {
    GitilesView view = GitilesView.repositoryIndex()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.REPOSITORY_INDEX);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"))
        .inOrder();
  }

  @Test
  public void refs() throws Exception {
    GitilesView view = GitilesView.refs()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.REFS);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+refs");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"))
        .inOrder();
  }

  @Test
  public void refWithRevision() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.revision()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.REVISION);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getPathPart()).isNull();
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"))
        .inOrder();
  }

  @Test
  public void describe() throws Exception {
    GitilesView view = GitilesView.describe()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setPathPart("deadbeef")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.DESCRIBE);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("deadbeef");
    assertThat(HOST.getParameters()).isEmpty();
  }

  @Test
  public void noPathComponents() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.PATH);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getPathPart()).isEqualTo("");
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master/");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"))
        .inOrder();
  }

  @Test
  public void onePathComponent() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/file")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.PATH);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getPathPart()).isEqualTo("file");
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master/file");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("file", "/b/foo/bar/+/master/file"))
        .inOrder();
  }

  @Test
  public void oneDocFileAuto() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.doc()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/README.md")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.DOC);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getPathPart()).isEqualTo("README.md");
    assertThat(HOST.getParameters()).isEmpty();
    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master/README.md");
  }

  @Test
  public void oneDocTree() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.doc()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/docs/")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.DOC);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getPathPart()).isEqualTo("docs");
    assertThat(HOST.getParameters()).isEmpty();
    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+doc/master/docs");
  }

  @Test
  public void showMarkdown() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.show()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/README.md")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.SHOW);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getPathPart()).isEqualTo("README.md");
    assertThat(HOST.getParameters()).isEmpty();
    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+show/master/README.md");
  }

  @Test
  public void rootedDoc() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.rootedDoc()
        .copyFrom(HOST)
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/docs/")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.ROOTED_DOC);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getPathPart()).isEqualTo("docs");
    assertThat(HOST.getParameters()).isEmpty();
    assertThat(view.toUrl()).isEqualTo("/b/docs");
  }

  @Test
  public void multiplePathComponents() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/path/to/a/file")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.PATH);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getPathPart()).isEqualTo("path/to/a/file");
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master/path/to/a/file");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("path", "/b/foo/bar/+/master/path"),
            breadcrumb("to", "/b/foo/bar/+/master/path/to"),
            breadcrumb("a", "/b/foo/bar/+/master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/master/path/to/a/file"))
        .inOrder();
  }

  @Test
  public void diffAgainstFirstParent() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    ObjectId parent = ObjectId.fromString("efab5678efab5678efab5678efab5678efab5678");
    GitilesView view = GitilesView.diff()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setOldRevision(Revision.unpeeled("master^", parent))
        .setPathPart("/path/to/a/file")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.DIFF);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
    assertThat(view.getPathPart()).isEqualTo("path/to/a/file");
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master%5E%21/path/to/a/file");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master^!", "/b/foo/bar/+/master%5E%21/"),
            breadcrumb(".", "/b/foo/bar/+/master%5E%21/"),
            breadcrumb("path", "/b/foo/bar/+/master%5E%21/path"),
            breadcrumb("to", "/b/foo/bar/+/master%5E%21/path/to"),
            breadcrumb("a", "/b/foo/bar/+/master%5E%21/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/master%5E%21/path/to/a/file"))
        .inOrder();
  }

  @Test
  public void diffAgainstEmptyRevision() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.diff()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/path/to/a/file")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.DIFF);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("path/to/a/file");
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master%5E%21/path/to/a/file");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master^!", "/b/foo/bar/+/master%5E%21/"),
            breadcrumb(".", "/b/foo/bar/+/master%5E%21/"),
            breadcrumb("path", "/b/foo/bar/+/master%5E%21/path"),
            breadcrumb("to", "/b/foo/bar/+/master%5E%21/path/to"),
            breadcrumb("a", "/b/foo/bar/+/master%5E%21/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/master%5E%21/path/to/a/file"))
        .inOrder();
  }

  @Test
  public void diffAgainstOther() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    ObjectId other = ObjectId.fromString("efab5678efab5678efab5678efab5678efab5678");
    GitilesView view = GitilesView.diff()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setOldRevision(Revision.unpeeled("efab5678", other))
        .setPathPart("/path/to/a/file")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.DIFF);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision().getName()).isEqualTo("efab5678");
    assertThat(view.getPathPart()).isEqualTo("path/to/a/file");
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/efab5678..master/path/to/a/file");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("efab5678..master", "/b/foo/bar/+/efab5678..master/"),
            breadcrumb(".", "/b/foo/bar/+/efab5678..master/"),
            breadcrumb("path", "/b/foo/bar/+/efab5678..master/path"),
            breadcrumb("to", "/b/foo/bar/+/efab5678..master/path/to"),
            breadcrumb("a", "/b/foo/bar/+/efab5678..master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/efab5678..master/path/to/a/file"))
        .inOrder();
  }

  @Test
  public void branchLogWithoutPath() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+log/master");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+log/master"))
        .inOrder();
  }

  @Test
  public void idLogWithoutPath() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("abcd1234", id))
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("abcd1234");
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+log/abcd1234");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("abcd1234", "/b/foo/bar/+log/abcd1234"))
        .inOrder();
  }

  @Test
  public void logWithoutOldRevision() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/path/to/a/file")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("path/to/a/file");
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+log/master/path/to/a/file");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+log/master"),
            breadcrumb("path", "/b/foo/bar/+log/master/path"),
            breadcrumb("to", "/b/foo/bar/+log/master/path/to"),
            breadcrumb("a", "/b/foo/bar/+log/master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+log/master/path/to/a/file"))
        .inOrder();
  }

  @Test
  public void logWithOldRevision() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    ObjectId parent = ObjectId.fromString("efab5678efab5678efab5678efab5678efab5678");
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setOldRevision(Revision.unpeeled("master^", parent))
        .setPathPart("/path/to/a/file")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision().getName()).isEqualTo("master^");
    assertThat(view.getPathPart()).isEqualTo("path/to/a/file");
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+log/master%5E..master/path/to/a/file");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master^..master", "/b/foo/bar/+log/master%5E..master"),
            breadcrumb("path", "/b/foo/bar/+log/master%5E..master/path"),
            breadcrumb("to", "/b/foo/bar/+log/master%5E..master/path/to"),
            breadcrumb("a", "/b/foo/bar/+log/master%5E..master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+log/master%5E..master/path/to/a/file"))
        .inOrder();
  }

  @Test
  public void logWithNoRevision() throws Exception {
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(HOST.getParameters()).isEmpty();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+log");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("HEAD", "/b/foo/bar/+log"))
        .inOrder();
  }

  @Test
  public void archiveWithNoPath() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.archive()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setExtension(".tar.bz2")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.ARCHIVE);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isNull();
    assertThat(HOST.getParameters()).isEmpty();
    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+archive/master.tar.bz2");
  }

  @Test
  public void archiveWithPath() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.archive()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/path/to/a/dir")
        .setExtension(".tar.bz2")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.ARCHIVE);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("path/to/a/dir");
    assertThat(HOST.getParameters()).isEmpty();
    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+archive/master/path/to/a/dir.tar.bz2");
  }

  @Test
  public void blame() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.blame()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/dir/file")
        .build();

    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.BLAME);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo/bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("master");
    assertThat(view.getOldRevision()).isEqualTo(Revision.NULL);
    assertThat(view.getPathPart()).isEqualTo("dir/file");
    assertThat(HOST.getParameters()).isEmpty();
    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+blame/master/dir/file");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("dir", "/b/foo/bar/+/master/dir"),
            breadcrumb("file", "/b/foo/bar/+blame/master/dir/file"))
        .inOrder();
  }

  @Test
  public void escaping() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    ObjectId parent = ObjectId.fromString("efab5678efab5678efab5678efab5678efab5678");
    // Some of these values are not valid for Git, but check them anyway.
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo?bar")
        .setRevision(Revision.unpeeled("ba/d#name", id))
        .setOldRevision(Revision.unpeeled("other\"na/me", parent))
        .setPathPart("we ird/pa'th/name")
        .putParam("k e y", "val/ue")
        .setAnchor("anc#hor")
        .build();

    // Fields returned by getters are not escaped.
    assertThat(view.getServletPath()).isEqualTo("/b");
    assertThat(view.getType()).isEqualTo(Type.LOG);
    assertThat(view.getHostName()).isEqualTo("host");
    assertThat(view.getRepositoryName()).isEqualTo("foo?bar");
    assertThat(view.getRevision().getId()).isEqualTo(id);
    assertThat(view.getRevision().getName()).isEqualTo("ba/d#name");
    assertThat(view.getOldRevision().getId()).isEqualTo(parent);
    assertThat(view.getOldRevision().getName()).isEqualTo("other\"na/me");
    assertThat(view.getPathPart()).isEqualTo("we ird/pa'th/name");
    assertThat(view.getParameters())
        .isEqualTo(ImmutableListMultimap.<String, String> of("k e y", "val/ue"));

    String qs = "?k+e+y=val%2Fue";
    assertThat(view.toUrl()).isEqualTo(
        "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird/pa%27th/name" + qs + "#anc%23hor");
    assertThat(view.getBreadcrumbs())
        .containsExactly(
            // Names are not escaped (auto-escaped by Soy) but values are.
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo?bar", "/b/foo%3Fbar/"),
            breadcrumb("other\"na/me..ba/d#name",
              "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name" + qs),
            breadcrumb("we ird",
              "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird" + qs),
            breadcrumb("pa'th",
              "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird/pa%27th" + qs),
            breadcrumb("name",
              "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird/pa%27th/name" + qs))
        .inOrder();
  }

  @Test
  public void breadcrumbsHasSingleTree() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("/path/to/a/file")
        .build();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master/path/to/a/file");
    assertThat(view.getBreadcrumbs(ImmutableList.of(false, true, true)))
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("path", "/b/foo/bar/+/master/path"),
            breadcrumb("to", "/b/foo/bar/+/master/path/to?autodive=0"),
            breadcrumb("a", "/b/foo/bar/+/master/path/to/a?autodive=0"),
            breadcrumb("file", "/b/foo/bar/+/master/path/to/a/file"))
        .inOrder();
    assertThat(view.getBreadcrumbs(ImmutableList.of(true, false, false)))
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("path", "/b/foo/bar/+/master/path?autodive=0"),
            breadcrumb("to", "/b/foo/bar/+/master/path/to"),
            breadcrumb("a", "/b/foo/bar/+/master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/master/path/to/a/file"))
        .inOrder();
  }

  @Test
  public void breadcrumbsHasSingleTreeRootPath() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setPathPart("")
        .build();

    assertThat(view.toUrl()).isEqualTo("/b/foo/bar/+/master/");
    assertThat(view.getBreadcrumbs(ImmutableList.<Boolean> of()))
        .containsExactly(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo", "/b/foo/"),
            breadcrumb("bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"))
        .inOrder();
  }

  private static ImmutableMap<String, String> breadcrumb(String text, String url) {
    return ImmutableMap.of("text", text, "url", url);
  }
}
