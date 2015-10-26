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
  private static final TypeToken<Map<String, RepositoryDescription>> REPOS =
      new TypeToken<Map<String, RepositoryDescription>>() {};

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
    assertThat(data).containsEntry("hostName", URLS.getHostName(null));
    assertThat(data).containsEntry("breadcrumbs", NullData.INSTANCE);
    assertThat(data).containsEntry("prefix", "");

    SoyListData repos = (SoyListData) data.get("repositories");
    assertThat(repos).hasSize(1);

    SoyMapData ent = (SoyMapData) repos.get(0);
    assertThat(ent.get("name").toString()).isEqualTo(NAME);
    assertThat(ent.get("url").toString()).isEqualTo("/b/" + NAME + "/");
  }

  @Test
  public void fooSubdirHtml() throws Exception {
    Map<String, ?> data = buildData("/foo/");
    assertThat(data).containsEntry("hostName", URLS.getHostName(null) + "/foo");
    assertThat(data).containsEntry("prefix", "foo/");

    SoyListData breadcrumbs = (SoyListData) data.get("breadcrumbs");
    assertThat(breadcrumbs.length()).isEqualTo(2);

    SoyListData repos = (SoyListData) data.get("repositories");
    assertThat(repos.length()).isEqualTo(1);

    SoyMapData ent = (SoyMapData) repos.get(0);
    assertThat(ent.get("name").toString()).isEqualTo("bar/repo");
    assertThat(ent.get("url").toString()).isEqualTo("/b/" + NAME + "/");
  }

  @Test
  public void fooBarSubdirHtml() throws Exception {
    Map<String, ?> data = buildData("/foo/bar/");
    assertThat(data).containsEntry("hostName", URLS.getHostName(null) + "/foo/bar");
    assertThat(data).containsEntry("prefix", "foo/bar/");

    SoyListData breadcrumbs = (SoyListData) data.get("breadcrumbs");
    assertThat(breadcrumbs.length()).isEqualTo(3);

    SoyListData repos = (SoyListData) data.get("repositories");
    assertThat(repos.length()).isEqualTo(1);

    SoyMapData ent = (SoyMapData) repos.get(0);
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
    Map<String, RepositoryDescription> res = buildJson("/", REPOS);

    assertThat(res).hasSize(1);
    assertThat(res).containsKey(name);
    RepositoryDescription d = res.get(name);
    assertThat(d.name).isEqualTo(name);
  }

  @Test
  public void fooSubdirJson() throws Exception {
    Map<String, RepositoryDescription> res = buildJson("/foo/", REPOS);

    assertThat(res).hasSize(1);
    assertThat(res).containsKey("bar/repo");
    RepositoryDescription d = res.get("bar/repo");
    assertThat(d.name).isEqualTo(repo.getRepository().getDescription().getRepositoryName());
  }

  @Test
  public void fooBarSubdirJson() throws Exception {
    Map<String, RepositoryDescription> res = buildJson("/foo/bar/", REPOS);

    assertThat(res).hasSize(1);
    assertThat(res).containsKey("repo");
    RepositoryDescription d = res.get("repo");
    assertThat(d.name).isEqualTo(repo.getRepository().getDescription().getRepositoryName());
  }

  @Test
  public void emptySubdirectoryList() throws Exception {
    assertNotFound("/no.repos/", null);
  }
}
