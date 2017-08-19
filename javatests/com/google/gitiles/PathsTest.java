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
import static com.google.gitiles.PathUtil.simplifyPathUpToRoot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PathUtil}. */
@RunWith(JUnit4.class)
public class PathsTest {
  @Test
  public void simplifyPathUpToRootSimplifiesPath() throws Exception {
    String root = "a/b/c";
    assertThat(simplifyPathUpToRoot("/foo", root)).isNull();
    assertThat(simplifyPathUpToRoot("../../", root)).isEqualTo("a");
    assertThat(simplifyPathUpToRoot(".././../", root)).isEqualTo("a");
    assertThat(simplifyPathUpToRoot("..//../", root)).isEqualTo("a");
    assertThat(simplifyPathUpToRoot("../../d", root)).isEqualTo("a/d");
    assertThat(simplifyPathUpToRoot("../../..", root)).isEqualTo("");
    assertThat(simplifyPathUpToRoot("../../d/e", root)).isEqualTo("a/d/e");
    assertThat(simplifyPathUpToRoot("../d/../e/../", root)).isEqualTo("a/b");
    assertThat(simplifyPathUpToRoot("../../../../", root)).isNull();
    assertThat(simplifyPathUpToRoot("../../a/../../..", root)).isNull();
  }

  @Test
  public void simplifyPathUpToNullRootDetectsNullRoot() throws Exception {
    assertThat(simplifyPathUpToRoot("/foo", null)).isNull();
    assertThat(simplifyPathUpToRoot("../", null)).isNull();
    assertThat(simplifyPathUpToRoot("../../", null)).isNull();
    assertThat(simplifyPathUpToRoot(".././../", null)).isNull();
    assertThat(simplifyPathUpToRoot("a/b", null)).isEqualTo("a/b");
    assertThat(simplifyPathUpToRoot("a/b/../c", null)).isEqualTo("a/c");
  }
}
