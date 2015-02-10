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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.google.gitiles.DefaultRenderer;
import com.google.gitiles.FakeHttpServletResponse;
import com.google.gitiles.GitilesView;
import com.google.gitiles.Renderer;
import com.google.gitiles.TestGitilesAccess;
import com.google.gitiles.TestViewFilter;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URL;

/** Tests for {DocServlet}. */
@RunWith(JUnit4.class)
public class DocServletTest {
  private static final Renderer RENDERER =
      new DefaultRenderer("/+static", ImmutableList.<URL> of(), "Test");

  private TestRepository<DfsRepository> repo;
  private DocServlet servlet;

  @Before
  public void setUp() throws Exception {
    DfsRepository r = new InMemoryRepository(new DfsRepositoryDescription("repo"));
    repo = new TestRepository<>(r);
    servlet = new DocServlet(new TestGitilesAccess(repo.getRepository()), RENDERER);
  }

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
    assertTrue(html.contains("<h1>" + title + "</h1>"));
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

    assertTrue(html.contains("<h1>page</h1>"));
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
    assertTrue(html.contains("<h1>B. Ad</h1>"));
    assertTrue(html.contains("Non-HTML is fine."));

    assertFalse(html.contains("window.alert"));
    assertFalse(html.contains("<script>"));
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

  private String buildHtml(String pathAndQuery) throws Exception {
    TestViewFilter.Result res = service(pathAndQuery);
    FakeHttpServletResponse http = res.getResponse();
    assertEquals("text/html", http.getHeader(HttpHeaders.CONTENT_TYPE));
    assertNotNull("has ETag", http.getHeader(HttpHeaders.ETAG));
    return http.getActualBodyString();
  }

  private TestViewFilter.Result service(String pathAndQuery) throws Exception {
    TestViewFilter.Result res = TestViewFilter.service(repo, pathAndQuery);
    assertEquals(200, res.getResponse().getStatus());
    assertEquals(GitilesView.Type.DOC, res.getView().getType());
    servlet.service(res.getRequest(), res.getResponse());
    return res;
  }
}
