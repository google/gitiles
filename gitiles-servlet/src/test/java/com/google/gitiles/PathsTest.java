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

import static com.google.gitiles.PathUtil.simplifyPathUpToRoot;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PathUtil}. */
@RunWith(JUnit4.class)
public class PathsTest {
  @Test
  public void simplifyPathUpToRootSimplifiesPath() throws Exception {
    String root = "a/b/c";
    assertNull(simplifyPathUpToRoot("/foo", root));
    assertEquals("a", simplifyPathUpToRoot("../../", root));
    assertEquals("a", simplifyPathUpToRoot(".././../", root));
    assertEquals("a", simplifyPathUpToRoot("..//../", root));
    assertEquals("a/d", simplifyPathUpToRoot("../../d", root));
    assertEquals("", simplifyPathUpToRoot("../../..", root));
    assertEquals("a/d/e", simplifyPathUpToRoot("../../d/e", root));
    assertEquals("a/b", simplifyPathUpToRoot("../d/../e/../", root));
    assertNull(simplifyPathUpToRoot("../../../../", root));
    assertNull(simplifyPathUpToRoot("../../a/../../..", root));
  }

  @Test
  public void simplifyPathUpToNullRootDetectsNullRoot() throws Exception {
    assertNull(simplifyPathUpToRoot("/foo", null));
    assertNull(simplifyPathUpToRoot("../", null));
    assertNull(simplifyPathUpToRoot("../../", null));
    assertNull(simplifyPathUpToRoot(".././../", null));
    assertEquals("a/b", simplifyPathUpToRoot("a/b", null));
    assertEquals("a/c", simplifyPathUpToRoot("a/b/../c", null));
  }
}
