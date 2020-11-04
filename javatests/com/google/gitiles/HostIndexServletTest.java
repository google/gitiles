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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gitiles.TestGitilesUrls.URLS;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gson.reflect.TypeToken;
import com.google.template.soy.data.restricted.NullData;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link HostIndexServlet}. */
@RunWith(JUnit4.class)
public class HostIndexServletTest extends ServletTest {
  private static final String NAME = "foo/bar/repo";
  private static final TypeToken<Map<String, RepositoryDescription>> REPOS =
      new TypeToken<Map<String, RepositoryDescription>>() {};

  @Override
  @Before
  public void setUp() throws Exception {
    repo = new TestRepository<>(new InMemoryRepository(new DfsRepositoryDescription(NAME)));
    servlet = TestGitilesServlet.create(repo);
  }

  @Test
  public void rootHtml() throws Exception {
    Map<String, Object> data = buildData("/");
    assertThat(data).containsEntry("hostName", URLS.getHostName(null));
    assertThat(data).containsEntry("breadcrumbs", NullData.INSTANCE);
    assertThat(data).containsEntry("prefix", "");

    ImmutableList<Map<String, Object>> repos =
        (ImmutableList<Map<String, Object>>) data.get("repositories");
    assertThat(repos).hasSize(1);

    Map<String, Object> ent = repos.get(0);
    assertThat(ent.get("name").toString()).isEqualTo(NAME);
    assertThat(ent.get("url").toString()).isEqualTo("/b/" + NAME + "/");
  }

  @Test
  public void fooSubdirHtml() throws Exception {
    Map<String, ?> data = buildData("/foo/");
    assertThat(data).containsEntry("hostName", URLS.getHostName(null) + "/foo");
    assertThat(data).containsEntry("prefix", "foo/");

    ImmutableList<Map<String, Object>> breadcrumbs =
        (ImmutableList<Map<String, Object>>) data.get("breadcrumbs");
    assertThat(breadcrumbs.size()).isEqualTo(2);

    ImmutableList<Map<String, Object>> repos =
        (ImmutableList<Map<String, Object>>) data.get("repositories");
    assertThat(repos.size()).isEqualTo(1);

    Map<String, Object> ent = repos.get(0);
    assertThat(ent.get("name").toString()).isEqualTo("bar/repo");
    assertThat(ent.get("url").toString()).isEqualTo("/b/" + NAME + "/");
  }

  @Test
  public void fooBarSubdirHtml() throws Exception {
    Map<String, ?> data = buildData("/foo/bar/");
    assertThat(data).containsEntry("hostName", URLS.getHostName(null) + "/foo/bar");
    assertThat(data).containsEntry("prefix", "foo/bar/");

    ImmutableList<Map<String, Object>> breadcrumbs =
        (ImmutableList<Map<String, Object>>) data.get("breadcrumbs");
    assertThat(breadcrumbs.size()).isEqualTo(3);

    ImmutableList<Map<String, Object>> repos =
        (ImmutableList<Map<String, Object>>) data.get("repositories");
    assertThat(repos.size()).isEqualTo(1);

    Map<String, Object> ent = repos.get(0);
    assertThat(ent.get("name").toString()).isEqualTo("repo");
    assertThat(ent.get("url").toString()).isEqualTo("/b/" + NAME + "/");
  }

  @Test
  public void rootText() throws Exception {
    String name = repo.getRepository().getDescription().getRepositoryName();
    FakeHttpServletResponse res = buildText("/");
    assertThat(new String(res.getActualBody(), UTF_8)).isEqualTo(name + "\n");
  }

  @Test
  public void fooSubdirText() throws Exception {
    FakeHttpServletResponse res = buildText("/foo/");
    assertThat(new String(res.getActualBody(), UTF_8)).isEqualTo("bar/repo\n");
  }

  @Test
  public void fooBarSubdirText() throws Exception {
    FakeHttpServletResponse res = buildText("/foo/bar/");
    assertThat(new String(res.getActualBody(), UTF_8)).isEqualTo("repo\n");
  }

  @Test
  public void rootJson() throws Exception {
    String name = repo.getRepository().getDescription().getRepositoryName();
    Map<String, RepositoryDescription> res = buildJson(REPOS, "/");

    assertThat(res).hasSize(1);
    assertThat(res).containsKey(name);
    RepositoryDescription d = res.get(name);
    assertThat(d.name).isEqualTo(name);
  }

  @Test
  public void fooSubdirJson() throws Exception {
    Map<String, RepositoryDescription> res = buildJson(REPOS, "/foo/");

    assertThat(res).hasSize(1);
    assertThat(res).containsKey("bar/repo");
    RepositoryDescription d = res.get("bar/repo");
    assertThat(d.name).isEqualTo(repo.getRepository().getDescription().getRepositoryName());
  }

  @Test
  public void fooBarSubdirJson() throws Exception {
    Map<String, RepositoryDescription> res = buildJson(REPOS, "/foo/bar/");

    assertThat(res).hasSize(1);
    assertThat(res).containsKey("repo");
    RepositoryDescription d = res.get("repo");
    assertThat(d.name).isEqualTo(repo.getRepository().getDescription().getRepositoryName());
  }

  @Test
  public void emptySubdirectoryList() throws Exception {
    assertNotFound("/no.repos/", null);
  }

  @Test
  public void headOnRoot() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setMethod("HEAD");
    req.setPathInfo("/");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void headOnMissingSubdir() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setMethod("HEAD");
    req.setPathInfo("/no.repos/");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  public void headOnPopulatedSubdir() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setMethod("HEAD");
    req.setPathInfo("/foo/");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }
}
