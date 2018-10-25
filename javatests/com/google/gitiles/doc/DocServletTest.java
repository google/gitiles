// Copyright (C) 2015 Google Inc. All Rights Reserved.
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

import com.google.gitiles.ServletTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {DocServlet}. */
@RunWith(JUnit4.class)
public class DocServletTest extends ServletTest {
  @Test
  public void simpleReadmeDoc() throws Exception {
    String title = "DocServletTest simpleDoc";
    String url = "http://daringfireball.net/projects/markdown/syntax";
    String markdown =
        "# " + title + "\n" + "\n" + "Tests the rendering of " + "[Markdown](" + url + ").";
    repo.branch("master").commit().add("README.md", markdown).create();

    String html = buildHtml("/repo/+doc/master/README.md");
    assertThat(html).contains("<title>" + title + "</title>");
    assertThat(html).contains(title + "</h1>");
    assertThat(html).contains("<a href=\"" + url + "\">Markdown</a>");
  }

  @Test
  public void includesNavbar() throws Exception {
    String navbar = "# Site Title\n" + "\n" + "* [Home](index.md)\n" + "* [README](README.md)\n";
    repo.branch("master")
        .commit()
        .add("README.md", "# page\n\nof information.")
        .add("navbar.md", navbar)
        .create();

    String html = buildHtml("/repo/+doc/master/README.md");
    assertThat(html).contains("<title>Site Title - page</title>");

    assertThat(html).contains("<span class=\"Header-anchorTitle\">Site Title</span>");
    assertThat(html).contains("<li><a href=\"/b/repo/+/master/index.md\">Home</a></li>");
    assertThat(html).contains("<li><a href=\"/b/repo/+/master/README.md\">README</a></li>");
    assertThat(html)
        .contains("<h1><a class=\"h\" name=\"page\" href=\"#page\"><span></span></a>page</h1>");
  }

  @Test
  public void simpleSubNavbar() throws Exception {
    String rootNavbar =
        "# Site Title\n\n* [Home](index.md)\n* [README](README.md)\n";
    String subNavbar =
        "# Subdir Title\n\n* [Sub Home](index.md)\n* [Sub README](README.md)\n";
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

  @Test
  public void dropsHtml() throws Exception {
    String markdown =
        "# B. Ad\n"
            + "\n"
            + "<script>window.alert();</script>\n"
            + "\n"
            + "Non-HTML <b>is fine</b>.";
    repo.branch("master").commit().add("index.md", markdown).create();

    String html = buildHtml("/repo/+doc/master/");
    assertThat(html).contains("B. Ad</h1>");
    assertThat(html).contains("Non-HTML is fine.");

    assertThat(html).doesNotContain("window.alert");
    assertThat(html).doesNotContain("<script>");
  }

  @Test
  public void namedAnchor() throws Exception {
    String markdown = "# Section {#debug}\n" + "# Other <a name=\"OLD-SCHOOL\"></a>\n";
    repo.branch("master").commit().add("index.md", markdown).create();
    String html = buildHtml("/repo/+doc/master/");
    assertThat(html)
        .contains(
            "<h1>"
                + "<a class=\"h\" name=\"debug\" href=\"#debug\"><span></span></a>"
                + "Section</h1>");
    assertThat(html)
        .contains(
            "<h1>"
                + "<a class=\"h\" name=\"OLD-SCHOOL\" href=\"#OLD-SCHOOL\"><span></span></a>"
                + "<a class=\"h\" name=\"old-school\" href=\"#old-school\"><span></span></a>"
                + "Other</h1>");
  }

  @Test
  public void incompleteHtmlIsLiteral() throws Exception {
    String markdown = "Incomplete <html is literal.";
    repo.branch("master").commit().add("index.md", markdown).create();

    String html = buildHtml("/repo/+doc/master/index.md");
    assertThat(html).contains("Incomplete &lt;html is literal.");
  }

  @Test
  public void noteInList() throws Exception {
    String markdown =
        "+ one\n\n" + "    ***aside\n" + "    remember this\n" + "    ***\n" + "\n" + "+ two\n";
    repo.branch("master").commit().add("index.md", markdown).create();

    String html = buildHtml("/repo/+/master/index.md");
    System.out.println(html);
    assertThat(html)
        .contains(
            "<ul><li><p>one</p><div class=\"aside\">remember this</div>"
                + "</li><li><p>two</p></li></ul>");
  }

  @Test
  public void relativeLink() throws Exception {
    repo.branch("master").commit().add("A/B/README.md", "[c](../../C)").create();

    String html = buildHtml("/repo/+doc/master/A/B/README.md");
    assertThat(html).contains("<a href=\"/b/repo/+/master/C\">c</a>");
  }

  @Test
  public void absoluteLink() throws Exception {
    repo.branch("master").commit().add("README.md", "[c](/x)").create();

    String html = buildHtml("/repo/+doc/master/README.md");
    assertThat(html).contains("<a href=\"/b/repo/+/master/x\">c</a>");
  }

  @Test
  public void gitUrlLink() throws Exception {
    repo.branch("master").commit().add("README.md", "[c](git://example.com/repo.git)").create();

    String html = buildHtml("/repo/+doc/master/README.md");
    assertThat(html).contains("<a href=\"git://example.com/repo.git\">c</a>");
  }

  @Test
  public void invalidGitUrlLink() throws Exception {
    repo.branch("master").commit().add("README.md", "[c](git://example.com/repo/..)").create();

    String html = buildHtml("/repo/+doc/master/README.md");
    assertThat(html).contains("<a href=\"about:invalid#zSoyz\">c</a>");
  }
}
