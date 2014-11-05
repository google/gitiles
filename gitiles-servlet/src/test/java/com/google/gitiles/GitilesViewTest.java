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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    assertEquals("", view.getServletPath());
    assertEquals(Type.HOST_INDEX, view.getType());
    assertEquals("host", view.getHostName());
    assertNull(view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/?format=HTML", view.toUrl());
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "host", "url", "/?format=HTML")),
        view.getBreadcrumbs());
  }

  @Test
  public void hostIndex() throws Exception {
    assertEquals("/b", HOST.getServletPath());
    assertEquals(Type.HOST_INDEX, HOST.getType());
    assertEquals("host", HOST.getHostName());
    assertNull(HOST.getRepositoryName());
    assertEquals(Revision.NULL, HOST.getRevision());
    assertNull(HOST.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/?format=HTML", HOST.toUrl());
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "host", "url", "/b/?format=HTML")),
        HOST.getBreadcrumbs());
  }

  @Test
  public void queryParams() throws Exception {
    GitilesView view = GitilesView.log().copyFrom(HOST)
        .setRepositoryName("repo")
        .setRevision(Revision.named("master"))
        .putParam("foo", "foovalue")
        .putParam("bar", "barvalue")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("repo", view.getRepositoryName());
    assertEquals(Revision.named("master"), view.getRevision());
    assertNull(view.getPathPart());
    assertEquals(
        ImmutableListMultimap.of(
            "foo", "foovalue",
            "bar", "barvalue"),
        view.getParameters());

    assertEquals("/b/repo/+log/master?foo=foovalue&bar=barvalue", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("repo", "/b/repo/"),
            breadcrumb("master", "/b/repo/+log/master?foo=foovalue&bar=barvalue")),
        view.getBreadcrumbs());
  }

  @Test
  public void queryParamsCopiedOnlyOnSameType() throws Exception {
    GitilesView view = GitilesView.repositoryIndex().copyFrom(HOST)
        .setRepositoryName("repo")
        .putParam("foo", "foovalue")
        .putParam("bar", "barvalue")
        .build();
    assertFalse(view.getParameters().isEmpty());
    assertEquals(view.getParameters(),
        GitilesView.repositoryIndex().copyFrom(view).build().getParameters());
    assertTrue(GitilesView.hostIndex().copyFrom(view).build().getParameters().isEmpty());
  }

  @Test
  public void repositoryIndex() throws Exception {
    GitilesView view = GitilesView.repositoryIndex()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.REPOSITORY_INDEX, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/")),
        view.getBreadcrumbs());
  }

  @Test
  public void refs() throws Exception {
    GitilesView view = GitilesView.refs()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.REFS, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+refs", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/")),
        view.getBreadcrumbs());
  }

  @Test
  public void refWithRevision() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.revision()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.REVISION, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertNull(view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+/master", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master")),
        view.getBreadcrumbs());
  }

  @Test
  public void describe() throws Exception {
    GitilesView view = GitilesView.describe()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setPathPart("deadbeef")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.DESCRIBE, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals("deadbeef", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.PATH, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+/master/", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/")),
        view.getBreadcrumbs());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.PATH, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("file", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+/master/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("file", "/b/foo/bar/+/master/file")),
        view.getBreadcrumbs());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.PATH, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("path/to/a/file", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+/master/path/to/a/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("path", "/b/foo/bar/+/master/path"),
            breadcrumb("to", "/b/foo/bar/+/master/path/to"),
            breadcrumb("a", "/b/foo/bar/+/master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/master/path/to/a/file")),
        view.getBreadcrumbs());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.DIFF, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals("path/to/a/file", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+/master%5E%21/path/to/a/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master^!", "/b/foo/bar/+/master%5E%21/"),
            breadcrumb(".", "/b/foo/bar/+/master%5E%21/"),
            breadcrumb("path", "/b/foo/bar/+/master%5E%21/path"),
            breadcrumb("to", "/b/foo/bar/+/master%5E%21/path/to"),
            breadcrumb("a", "/b/foo/bar/+/master%5E%21/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/master%5E%21/path/to/a/file")),
        view.getBreadcrumbs());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.DIFF, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("path/to/a/file", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+/master%5E%21/path/to/a/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master^!", "/b/foo/bar/+/master%5E%21/"),
            breadcrumb(".", "/b/foo/bar/+/master%5E%21/"),
            breadcrumb("path", "/b/foo/bar/+/master%5E%21/path"),
            breadcrumb("to", "/b/foo/bar/+/master%5E%21/path/to"),
            breadcrumb("a", "/b/foo/bar/+/master%5E%21/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/master%5E%21/path/to/a/file")),
        view.getBreadcrumbs());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.DIFF, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("efab5678", view.getOldRevision().getName());
    assertEquals("path/to/a/file", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+/efab5678..master/path/to/a/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("efab5678..master", "/b/foo/bar/+/efab5678..master/"),
            breadcrumb(".", "/b/foo/bar/+/efab5678..master/"),
            breadcrumb("path", "/b/foo/bar/+/efab5678..master/path"),
            breadcrumb("to", "/b/foo/bar/+/efab5678..master/path/to"),
            breadcrumb("a", "/b/foo/bar/+/efab5678..master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/efab5678..master/path/to/a/file")),
        view.getBreadcrumbs());
  }

  @Test
  public void branchLogWithoutPath() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertNull(view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+log/master", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+log/master")),
        view.getBreadcrumbs());
  }

  @Test
  public void idLogWithoutPath() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("abcd1234", id))
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("abcd1234", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertNull(view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+log/abcd1234", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("abcd1234", "/b/foo/bar/+log/abcd1234")),
        view.getBreadcrumbs());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("path/to/a/file", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+log/master/path/to/a/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+log/master"),
            breadcrumb("path", "/b/foo/bar/+log/master/path"),
            breadcrumb("to", "/b/foo/bar/+log/master/path/to"),
            breadcrumb("a", "/b/foo/bar/+log/master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+log/master/path/to/a/file")),
        view.getBreadcrumbs());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals("path/to/a/file", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+log/master%5E..master/path/to/a/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master^..master", "/b/foo/bar/+log/master%5E..master"),
            breadcrumb("path", "/b/foo/bar/+log/master%5E..master/path"),
            breadcrumb("to", "/b/foo/bar/+log/master%5E..master/path/to"),
            breadcrumb("a", "/b/foo/bar/+log/master%5E..master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+log/master%5E..master/path/to/a/file")),
        view.getBreadcrumbs());
  }

  @Test
  public void logWithNoRevision() throws Exception {
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+log", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("HEAD", "/b/foo/bar/+log")),
        view.getBreadcrumbs());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.ARCHIVE, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertNull(view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());
    assertEquals("/b/foo/bar/+archive/master.tar.bz2", view.toUrl());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.ARCHIVE, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("path/to/a/dir", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());
    assertEquals("/b/foo/bar/+archive/master/path/to/a/dir.tar.bz2", view.toUrl());
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

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.BLAME, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("dir/file", view.getPathPart());
    assertTrue(HOST.getParameters().isEmpty());
    assertEquals("/b/foo/bar/+blame/master/dir/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("dir", "/b/foo/bar/+/master/dir"),
            breadcrumb("file", "/b/foo/bar/+blame/master/dir/file")),
        view.getBreadcrumbs());
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
    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo?bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("ba/d#name", view.getRevision().getName());
    assertEquals(parent, view.getOldRevision().getId());
    assertEquals("other\"na/me", view.getOldRevision().getName());
    assertEquals("we ird/pa'th/name", view.getPathPart());
    assertEquals(ImmutableListMultimap.<String, String> of("k e y", "val/ue"),
        view.getParameters());

    String qs = "?k+e+y=val%2Fue";
    assertEquals(
        "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird/pa%27th/name" + qs + "#anc%23hor",
        view.toUrl());
    assertEquals(
        ImmutableList.of(
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
              "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird/pa%27th/name" + qs)),
        view.getBreadcrumbs());
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

    assertEquals("/b/foo/bar/+/master/path/to/a/file", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("path", "/b/foo/bar/+/master/path"),
            breadcrumb("to", "/b/foo/bar/+/master/path/to?autodive=0"),
            breadcrumb("a", "/b/foo/bar/+/master/path/to/a?autodive=0"),
            breadcrumb("file", "/b/foo/bar/+/master/path/to/a/file")),
        view.getBreadcrumbs(ImmutableList.of(false, true, true)));
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/"),
            breadcrumb("path", "/b/foo/bar/+/master/path?autodive=0"),
            breadcrumb("to", "/b/foo/bar/+/master/path/to"),
            breadcrumb("a", "/b/foo/bar/+/master/path/to/a"),
            breadcrumb("file", "/b/foo/bar/+/master/path/to/a/file")),
        view.getBreadcrumbs(ImmutableList.of(true, false, false)));
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

    assertEquals("/b/foo/bar/+/master/", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master"),
            breadcrumb(".", "/b/foo/bar/+/master/")),
        view.getBreadcrumbs(ImmutableList.<Boolean> of()));
  }

  private static ImmutableMap<String, String> breadcrumb(String text, String url) {
    return ImmutableMap.of("text", text, "url", url);
  }
}
