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
import static com.google.gitiles.TreeSoyData.getTargetDisplayName;
import static com.google.gitiles.TreeSoyData.resolveTargetUrl;

import com.google.common.base.Strings;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TreeSoyData}. */
@RunWith(JUnit4.class)
public class TreeSoyDataTest {
  @Test
  public void getTargetDisplayNameReturnsDisplayName() throws Exception {
    assertThat(getTargetDisplayName("foo")).isEqualTo("foo");
    assertThat(getTargetDisplayName("foo/bar")).isEqualTo("foo/bar");
    assertThat(getTargetDisplayName(Strings.repeat("a/", 10) + "bar")).isEqualTo(
        "a/a/a/a/a/a/a/a/a/a/bar");
    assertThat(getTargetDisplayName(Strings.repeat("a/", 34) + "bar")).isEqualTo(
        "a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/a/bar");
    assertThat(getTargetDisplayName(Strings.repeat("a/", 35) + "bar")).isEqualTo(".../bar");
    assertThat(getTargetDisplayName(Strings.repeat("a/", 100) + "bar")).isEqualTo(".../bar");
    assertThat(getTargetDisplayName(Strings.repeat("a", 80))).isEqualTo(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  }

  @Test
  public void resolveTargetUrlReturnsUrl() throws Exception {
    ObjectId id = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
    GitilesView view = GitilesView.path()
        .setServletPath("/x")
        .setHostName("host")
        .setRepositoryName("repo")
        .setRevision(Revision.unpeeled("m", id))
        .setPathPart("a/b/c")
        .build();
    assertThat(resolveTargetUrl(view, "/foo")).isNull();
    assertThat(resolveTargetUrl(view, "../../")).isEqualTo("/x/repo/+/m/a");
    assertThat(resolveTargetUrl(view, ".././../")).isEqualTo("/x/repo/+/m/a");
    assertThat(resolveTargetUrl(view, "..//../")).isEqualTo("/x/repo/+/m/a");
    assertThat(resolveTargetUrl(view, "../../d")).isEqualTo("/x/repo/+/m/a/d");
    assertThat(resolveTargetUrl(view, "../../..")).isEqualTo("/x/repo/+/m/");
    assertThat(resolveTargetUrl(view, "../../d/e")).isEqualTo("/x/repo/+/m/a/d/e");
    assertThat(resolveTargetUrl(view, "../d/../e/../")).isEqualTo("/x/repo/+/m/a/b");
    assertThat(resolveTargetUrl(view, "../../../../")).isNull();
    assertThat(resolveTargetUrl(view, "../../a/../../..")).isNull();
  }
}
