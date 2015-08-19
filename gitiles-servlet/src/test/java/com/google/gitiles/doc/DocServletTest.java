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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    String markdown = "# " + title + "\n"
        + "\n"
        + "Tests the rendering of "
        + "[Markdown](" + url + ").";
    repo.branch("master").commit()
        .add("README.md", markdown)
        .create();

    String html = buildHtml("/repo/+doc/master/README.md");
    assertTrue(html.contains("<title>" + title + "</title>"));
    assertTrue(html.contains(title + "</h1>"));
    assertTrue(html.contains("<a href=\"" + url + "\">Markdown</a>"));
  }

  @Test
  public void includesNavbar() throws Exception {
    String navbar = "# Site Title\n"
        + "\n"
        + "* [Home](index.md)\n"
        + "* [README](README.md)\n";
    repo.branch("master").commit()
        .add("README.md", "# page\n\nof information.")
        .add("navbar.md", navbar)
        .create();

    String html = buildHtml("/repo/+doc/master/README.md");
    assertTrue(html.contains("<title>Site Title - page</title>"));

    assertTrue(html.contains("<h1>Site Title</h1>"));
    assertTrue(html.contains("<h2>page</h2>"));
    assertTrue(html.contains("<li><a href=\"index.md\">Home</a></li>"));
    assertTrue(html.contains("<li><a href=\"README.md\">README</a></li>"));
    assertTrue(html.contains("<h1>"
        + "<a class=\"h\" name=\"page\" href=\"#page\"><span></span></a>"
        + "page</h1>"));
  }

  @Test
  public void dropsHtml() throws Exception {
    String markdown = "# B. Ad\n"
        + "\n"
        + "<script>window.alert();</script>\n"
        + "\n"
        + "Non-HTML <b>is fine</b>.";
    repo.branch("master").commit()
        .add("index.md", markdown)
        .create();

    String html = buildHtml("/repo/+doc/master/");
    assertTrue(html.contains("B. Ad</h1>"));
    assertTrue(html.contains("Non-HTML is fine."));

    assertFalse(html.contains("window.alert"));
    assertFalse(html.contains("<script>"));
  }

  @Test
  public void namedAnchor() throws Exception {
    String markdown = "# Section {#debug}\n"
        + "# Other <a name=\"old-school\"></a>\n";
    repo.branch("master").commit()
      .add("index.md", markdown)
      .create();
    String html = buildHtml("/repo/+doc/master/");
    assertTrue(html.contains("<h1>"
        + "<a class=\"h\" name=\"debug\" href=\"#debug\"><span></span></a>"
        + "Section</h1>"));
    assertTrue(html.contains("<h1>"
        + "<a class=\"h\" name=\"old-school\" href=\"#old-school\"><span></span></a>"
        + "Other</h1>"));
  }

  @Test
  public void incompleteHtmlIsLiteral() throws Exception {
    String markdown = "Incomplete <html is literal.";
    repo.branch("master").commit()
        .add("index.md", markdown)
        .create();

    String html = buildHtml("/repo/+doc/master/index.md");
    assertTrue(html.contains("Incomplete &lt;html is literal."));
  }

  @Test
  public void relativeLink() throws Exception {
    repo.branch("master").commit()
        .add("A/B/README.md", "[c](../../C)")
        .create();

    String html = buildHtml("/repo/+doc/master/A/B/README.md");
    assertTrue(html.contains("<a href=\"/b/repo/+show/master/C\">c</a>"));
  }

  @Test
  public void absoluteLink() throws Exception {
    repo.branch("master").commit()
        .add("README.md", "[c](/x)")
        .create();

    String html = buildHtml("/repo/+doc/master/README.md");
    assertTrue(html.contains("<a href=\"/b/repo/+show/master/x\">c</a>"));
  }

}
