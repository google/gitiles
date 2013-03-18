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

import static com.google.gitiles.TreeSoyData.getTargetDisplayName;
import static com.google.gitiles.TreeSoyData.resolveTargetUrl;
import junit.framework.TestCase;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.base.Strings;

/** Tests for {@link TreeSoyData}. */
public class TreeSoyDataTest extends TestCase {
  public void testGetTargetDisplayName() throws Exception {
    assertEquals("foo", getTargetDisplayName("foo"));
    assertEquals("foo/bar", getTargetDisplayName("foo/bar"));
    assertEquals("a/a/a/a/a/a/a/a/a/a/bar",
        getTargetDisplayName(Strings.repeat("a/", 10) + "bar"));
    assertEquals("a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/bar",
        getTargetDisplayName(Strings.repeat("a/", 34) + "bar"));
    assertEquals(".../bar", getTargetDisplayName(Strings.repeat("a/", 35) + "bar"));
    assertEquals(".../bar", getTargetDisplayName(Strings.repeat("a/", 100) + "bar"));
    assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        getTargetDisplayName(Strings.repeat("a", 80)));
  }

  public void testResolveTargetUrl() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .setServletPath("/x")
        .setHostName("host")
        .setRepositoryName("repo")
        .setRevision(Revision.unpeeled("m", id))
        .setPathPart("a/b/c")
        .build();
    assertNull(resolveTargetUrl(view, "/foo"));
    assertEquals("/x/repo/+/m/a", resolveTargetUrl(view, "../../"));
    assertEquals("/x/repo/+/m/a", resolveTargetUrl(view, ".././../"));
    assertEquals("/x/repo/+/m/a", resolveTargetUrl(view, "..//../"));
    assertEquals("/x/repo/+/m/a/d", resolveTargetUrl(view, "../../d"));
    assertEquals("/x/repo/+/m/", resolveTargetUrl(view, "../../.."));
    assertEquals("/x/repo/+/m/a/d/e", resolveTargetUrl(view, "../../d/e"));
    assertEquals("/x/repo/+/m/a/b", resolveTargetUrl(view, "../d/../e/../"));
    assertNull(resolveTargetUrl(view, "../../../../"));
    assertNull(resolveTargetUrl(view, "../../a/../../.."));
  }
}
