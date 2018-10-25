// Copyright (C) 2018 Google Inc. All Rights Reserved.
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

package com.google.gitiles.doc;

import static com.google.common.truth.Truth.assertThat;

import com.google.gitiles.TestGitilesAccess;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for Subnavbar extending DocServletTest for regressions. */
@RunWith(JUnit4.class)
public class SubNavbarServletTest extends DocServletTest {
  @Override
  public void setUp() throws Exception {
    Config subNavbarConfig = TestGitilesAccess.createDefaultConfig();
    subNavbarConfig.setBoolean("markdown", null, "subnavbar", true);
    overrideConfig(subNavbarConfig);
    super.setUp();
  }

  @Test
  public void simpleSubNavbar() throws Exception {
    String rootNavbar =
        "# Site Title\n" + "\n" + "* [Home](index.md)\n" + "* [README](README.md)\n";
    String subNavbar =
        "# Subdir Title\n" + "\n" + "* [Sub Home](index.md)\n" + "* [Sub README](README.md)\n";
    repo.branch("master")
        .commit()
        .add("README.md", "# page\n\nof information.")
        .add("navbar.md", rootNavbar)
        .add("subdir/README.md", "# subdir page\n\nof information.")
        .add("subdir/navbar.md", subNavbar)
        .create();

    String rootReadmeHtml = buildHtml("/repo/+doc/master/README.md");
    assertThat(rootReadmeHtml).contains("<title>Site Title - page</title>");

    assertThat(rootReadmeHtml).contains("<span class=\"Header-anchorTitle\">Site Title</span>");
    assertThat(rootReadmeHtml).contains("<li><a href=\"/b/repo/+/master/index.md\">Home</a></li>");
    assertThat(rootReadmeHtml)
        .contains("<li><a href=\"/b/repo/+/master/README.md\">README</a></li>");
    assertThat(rootReadmeHtml)
        .contains("<h1><a class=\"h\" name=\"page\" href=\"#page\"><span></span></a>page</h1>");

    String subdirReadmeHtml = buildHtml("/repo/+doc/master/subdir/README.md");
    assertThat(subdirReadmeHtml).contains("<title>Subdir Title - subdir page</title>");

    assertThat(subdirReadmeHtml).contains("<span class=\"Header-anchorTitle\">Subdir Title</span>");
    assertThat(subdirReadmeHtml)
        .contains("<li><a href=\"/b/repo/+/master/subdir/index.md\">Sub Home</a></li>");
    assertThat(subdirReadmeHtml)
        .contains("<li><a href=\"/b/repo/+/master/subdir/README.md\">Sub README</a></li>");
    assertThat(subdirReadmeHtml)
        .contains(
            "<h1><a class=\"h\" name=\"subdir-page\" href=\"#subdir-page\"><span></span>"
                + "</a>subdir page</h1>");
    assertThat(subdirReadmeHtml)
        .doesNotContain("<li><a href=\"/b/repo/+/master/index.md\">Home</a></li>");
    assertThat(subdirReadmeHtml)
        .doesNotContain("<li><a href=\"/b/repo/+/master/README.md\">README</a></li>");
  }
}
