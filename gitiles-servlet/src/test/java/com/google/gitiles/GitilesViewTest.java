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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.gitiles.GitilesView.Type;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.ObjectId;

/** Tests for Gitiles views. */
public class GitilesViewTest extends TestCase {
  private static final GitilesView HOST = GitilesView.hostIndex()
      .setServletPath("/b")
      .setHostName("host")
      .build();

  public void testEmptyServletPath() throws Exception {
    GitilesView view = GitilesView.hostIndex()
        .setServletPath("")
        .setHostName("host")
        .build();
    assertEquals("", view.getServletPath());
    assertEquals(Type.HOST_INDEX, view.getType());
    assertEquals("host", view.getHostName());
    assertNull(view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getTreePath());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/?format=HTML", view.toUrl());
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "host", "url", "/?format=HTML")),
        view.getBreadcrumbs());
  }

  public void testHostIndex() throws Exception {
    assertEquals("/b", HOST.getServletPath());
    assertEquals(Type.HOST_INDEX, HOST.getType());
    assertEquals("host", HOST.getHostName());
    assertNull(HOST.getRepositoryName());
    assertEquals(Revision.NULL, HOST.getRevision());
    assertNull(HOST.getTreePath());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/?format=HTML", HOST.toUrl());
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "host", "url", "/b/?format=HTML")),
        HOST.getBreadcrumbs());
  }

  public void testQueryParams() throws Exception {
    GitilesView view = GitilesView.hostIndex().copyFrom(HOST)
        .putParam("foo", "foovalue")
        .putParam("bar", "barvalue")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.HOST_INDEX, view.getType());
    assertEquals("host", view.getHostName());
    assertNull(view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getTreePath());
    assertEquals(
        ImmutableListMultimap.of(
            "foo", "foovalue",
            "bar", "barvalue"),
        view.getParameters());

    assertEquals("/b/?format=HTML&foo=foovalue&bar=barvalue", view.toUrl());
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "host", "url", "/b/?format=HTML")),
        view.getBreadcrumbs());
  }

  public void testQueryParamsNotCopied() throws Exception {
    GitilesView view = GitilesView.hostIndex().copyFrom(HOST)
        .putParam("foo", "foovalue")
        .putParam("bar", "barvalue")
        .build();
    GitilesView copy = GitilesView.hostIndex().copyFrom(view).build();
    assertFalse(view.getParameters().isEmpty());
    assertTrue(copy.getParameters().isEmpty());
  }

  public void testRepositoryIndex() throws Exception {
    GitilesView view = GitilesView.repositoryIndex()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.REPOSITORY_INDEX, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getTreePath());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/")),
        view.getBreadcrumbs());
  }

  public void testRefs() throws Exception {
    GitilesView view = GitilesView.refs()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.REFS, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(Revision.NULL, view.getRevision());
    assertNull(view.getTreePath());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+refs", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/")),
        view.getBreadcrumbs());
  }

  public void testRefWithRevision() throws Exception {
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
    assertNull(view.getTreePath());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+/master", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+/master")),
        view.getBreadcrumbs());
  }

  public void testNoPathComponents() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setTreePath("/")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.PATH, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("", view.getTreePath());
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

  public void testOnePathComponent() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setTreePath("/file")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.PATH, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("file", view.getTreePath());
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

  public void testMultiplePathComponents() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setTreePath("/path/to/a/file")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.PATH, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("path/to/a/file", view.getTreePath());
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

  public void testDiffAgainstFirstParent() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    ObjectId parent = ObjectId.fromString("efab5678efab5678efab5678efab5678efab5678");
    GitilesView view = GitilesView.diff()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setOldRevision(Revision.unpeeled("master^", parent))
        .setTreePath("/path/to/a/file")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.DIFF, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals("path/to/a/file", view.getTreePath());
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

  public void testDiffAgainstEmptyRevision() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.diff()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setTreePath("/path/to/a/file")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.DIFF, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("path/to/a/file", view.getTreePath());
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

  public void testDiffAgainstOther() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    ObjectId other = ObjectId.fromString("efab5678efab5678efab5678efab5678efab5678");
    GitilesView view = GitilesView.diff()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setOldRevision(Revision.unpeeled("efab5678", other))
        .setTreePath("/path/to/a/file")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.DIFF, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("efab5678", view.getOldRevision().getName());
    assertEquals("path/to/a/file", view.getTreePath());
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

  public void testBranchLogWithoutPath() throws Exception {
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
    assertNull(view.getTreePath());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+log/master", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("master", "/b/foo/bar/+log/master")),
        view.getBreadcrumbs());
  }

  public void testIdLogWithoutPath() throws Exception {
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
    assertNull(view.getTreePath());
    assertTrue(HOST.getParameters().isEmpty());

    assertEquals("/b/foo/bar/+log/abcd1234", view.toUrl());
    assertEquals(
        ImmutableList.of(
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo/bar", "/b/foo/bar/"),
            breadcrumb("abcd1234", "/b/foo/bar/+log/abcd1234")),
        view.getBreadcrumbs());
  }

  public void testLogWithoutOldRevision() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setTreePath("/path/to/a/file")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals(Revision.NULL, view.getOldRevision());
    assertEquals("path/to/a/file", view.getTreePath());
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

  public void testLogWithOldRevision() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    ObjectId parent = ObjectId.fromString("efab5678efab5678efab5678efab5678efab5678");
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setOldRevision(Revision.unpeeled("master^", parent))
        .setTreePath("/path/to/a/file")
        .build();

    assertEquals("/b", view.getServletPath());
    assertEquals(Type.LOG, view.getType());
    assertEquals("host", view.getHostName());
    assertEquals("foo/bar", view.getRepositoryName());
    assertEquals(id, view.getRevision().getId());
    assertEquals("master", view.getRevision().getName());
    assertEquals("master^", view.getOldRevision().getName());
    assertEquals("path/to/a/file", view.getTreePath());
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

  public void testEscaping() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    ObjectId parent = ObjectId.fromString("efab5678efab5678efab5678efab5678efab5678");
    // Some of these values are not valid for Git, but check them anyway.
    GitilesView view = GitilesView.log()
        .copyFrom(HOST)
        .setRepositoryName("foo?bar")
        .setRevision(Revision.unpeeled("ba/d#name", id))
        .setOldRevision(Revision.unpeeled("other\"na/me", parent))
        .setTreePath("we ird/pa'th/name")
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
    assertEquals("we ird/pa'th/name", view.getTreePath());
    assertEquals(ImmutableListMultimap.<String, String> of("k e y", "val/ue"),
        view.getParameters());

    assertEquals(
        "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird/pa%27th/name"
        + "?k+e+y=val%2Fue#anc%23hor", view.toUrl());
    assertEquals(
        ImmutableList.of(
            // Names are not escaped (auto-escaped by Soy) but values are.
            breadcrumb("host", "/b/?format=HTML"),
            breadcrumb("foo?bar", "/b/foo%3Fbar/"),
            breadcrumb("other\"na/me..ba/d#name", "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name"),
            breadcrumb("we ird", "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird"),
            breadcrumb("pa'th", "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird/pa%27th"),
            breadcrumb("name",
              "/b/foo%3Fbar/+log/other%22na/me..ba/d%23name/we%20ird/pa%27th/name")),
        view.getBreadcrumbs());
  }

  public void testBreadcrumbsHasSingleTree() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .copyFrom(HOST)
        .setRepositoryName("foo/bar")
        .setRevision(Revision.unpeeled("master", id))
        .setTreePath("/path/to/a/file")
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

  private static ImmutableMap<String, String> breadcrumb(String text, String url) {
    return ImmutableMap.of("text", text, "url", url);
  }
}
