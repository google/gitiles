// Copyright (C) 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles;

import static com.google.gitiles.TestGitilesUrls.URLS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.google.gson.reflect.TypeToken;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.restricted.NullData;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/** Tests for {@link HostIndexServlet}. */
@RunWith(JUnit4.class)
public class HostIndexServletTest extends ServletTest {
  private static final String NAME = "foo/bar/repo";

  @Override
  @Before
  public void setUp() throws Exception {
    repo = new TestRepository<DfsRepository>(
        new InMemoryRepository(new DfsRepositoryDescription(NAME)));
    servlet = TestGitilesServlet.create(repo);
  }

  @Test
  public void rootHtml() throws Exception {
    Map<String, ?> data = buildData("/");
    assertEquals(URLS.getHostName(null), data.get("hostName"));
    assertSame(NullData.INSTANCE, data.get("breadcrumbs"));
    assertEquals("", data.get("prefix"));

    SoyListData repos = (SoyListData) data.get("repositories");
    assertEquals(1, repos.length());

    SoyMapData ent = (SoyMapData) repos.get(0);
    assertEquals(NAME, ent.get("name").toString());
    assertEquals("/b/" + NAME + "/", ent.get("url").toString());
  }

  @Test
  public void fooSubdirHtml() throws Exception {
    Map<String, ?> data = buildData("/foo/");
    assertEquals(URLS.getHostName(null) + "/foo", data.get("hostName"));
    assertEquals("foo/", data.get("prefix"));

    SoyListData breadcrumbs = (SoyListData) data.get("breadcrumbs");
    assertEquals(2, breadcrumbs.length());

    SoyListData repos = (SoyListData) data.get("repositories");
    assertEquals(1, repos.length());

    SoyMapData ent = (SoyMapData) repos.get(0);
    assertEquals("bar/repo", ent.get("name").toString());
    assertEquals("/b/" + NAME + "/", ent.get("url").toString());
  }

  @Test
  public void fooBarSubdirHtml() throws Exception {
    Map<String, ?> data = buildData("/foo/bar/");
    assertEquals(URLS.getHostName(null) + "/foo/bar", data.get("hostName"));
    assertEquals("foo/bar/", data.get("prefix"));

    SoyListData breadcrumbs = (SoyListData) data.get("breadcrumbs");
    assertEquals(3, breadcrumbs.length());

    SoyListData repos = (SoyListData) data.get("repositories");
    assertEquals(1, repos.length());

    SoyMapData ent = (SoyMapData) repos.get(0);
    assertEquals("repo", ent.get("name").toString());
    assertEquals("/b/" + NAME + "/", ent.get("url").toString());
  }

  @Test
  public void rootText() throws Exception {
    String name = repo.getRepository().getDescription().getRepositoryName();
    FakeHttpServletResponse res = buildText("/");
    assertEquals(name + "\n", new String(res.getActualBody(), UTF_8));
  }

  @Test
  public void fooSubdirText() throws Exception {
    FakeHttpServletResponse res = buildText("/foo/");
    assertEquals("bar/repo\n", new String(res.getActualBody(), UTF_8));
  }

  @Test
  public void fooBarSubdirText() throws Exception {
    FakeHttpServletResponse res = buildText("/foo/bar/");
    assertEquals("repo\n", new String(res.getActualBody(), UTF_8));
  }

  @Test
  public void rootJson() throws Exception {
    String name = repo.getRepository().getDescription().getRepositoryName();
    Map<String, RepositoryDescription> res = buildJson(
        "/",
        new TypeToken<Map<String, RepositoryDescription>>() {}.getType());

    assertEquals(1, res.size());
    RepositoryDescription d = res.get(name);
    assertNotNull(name + " exists", d);
    assertEquals(name, d.name);
  }

  @Test
  public void fooSubdirJson() throws Exception {
    Map<String, RepositoryDescription> res = buildJson(
        "/foo/",
        new TypeToken<Map<String, RepositoryDescription>>() {}.getType());

    assertEquals(1, res.size());
    RepositoryDescription d = res.get("bar/repo");
    assertNotNull("bar/repo exists", d);
    assertEquals(repo.getRepository().getDescription().getRepositoryName(), d.name);
  }

  @Test
  public void fooBarSubdirJson() throws Exception {
    Map<String, RepositoryDescription> res = buildJson(
        "/foo/bar/",
        new TypeToken<Map<String, RepositoryDescription>>() {}.getType());

    assertEquals(1, res.size());
    RepositoryDescription d = res.get("repo");
    assertNotNull("repo exists", d);
    assertEquals(repo.getRepository().getDescription().getRepositoryName(), d.name);
  }

  @Test
  public void emptySubdirectoryList() throws Exception {
    assertNotFound("/no.repos/", null);
  }
}
