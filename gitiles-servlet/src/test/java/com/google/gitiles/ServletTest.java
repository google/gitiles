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

package com.google.gitiles;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.net.HttpHeaders;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;

import java.lang.reflect.Type;
import java.util.Map;

/** Base class for servlet tests. */
public class ServletTest {
  protected TestRepository<DfsRepository> repo;
  protected GitilesServlet servlet;

  @Before
  public void setUp() throws Exception {
    repo = new TestRepository<DfsRepository>(
        new InMemoryRepository(new DfsRepositoryDescription("repo")));
    servlet = TestGitilesServlet.create(repo);
  }

  protected FakeHttpServletResponse buildResponse(
      String path, String queryString, int expectedStatus)
      throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo(path);
    if (queryString != null) {
      req.setQueryString(queryString);
    }
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);
    assertThat(res.getStatus()).isEqualTo(expectedStatus);
    return res;
  }

  protected FakeHttpServletResponse build(String path) throws Exception {
    return buildResponse(path, null, SC_OK);
  }

  protected String buildHtml(String path, boolean assertHasETag) throws Exception {
    FakeHttpServletResponse res = build(path);
    assertThat(res.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/html");
    if (assertHasETag) {
      assertWithMessage("missing ETag").that(res.getHeader(HttpHeaders.ETAG)).isNotNull();
    }
    return res.getActualBodyString();
  }

  protected String buildHtml(String path) throws Exception {
    return buildHtml(path, true);
  }

  protected Map<String, ?> buildData(String path) throws Exception {
    // Render the page through Soy to ensure templates are valid, then return
    // the Soy data for introspection.
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo(path);
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);
    return BaseServlet.getData(req);
  }

  protected FakeHttpServletResponse buildText(String path) throws Exception {
    FakeHttpServletResponse res = buildResponse(path, "format=text", SC_OK);
    assertThat(res.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/plain");
    return res;
  }

  private String buildJsonRaw(String path, String additionalQueryString) throws Exception {
    FakeHttpServletResponse res = buildResponse(path, "format=json" + additionalQueryString, SC_OK);
    assertThat(res.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
    String body = res.getActualBodyString();
    String magic = ")]}'\n";
    assertThat(body).startsWith(magic);
    return body.substring(magic.length());
  }

  protected <T> T buildJson(String path, Class<T> classOfT, String additionalQueryString)
      throws Exception {
    return newGson().fromJson(buildJsonRaw(path, additionalQueryString), classOfT);
  }

  protected <T> T buildJson(String path, Class<T> classOfT) throws Exception {
    return buildJson(path, classOfT, "");
  }

  protected <T> T buildJson(String path, Type typeOfT, String additionalQueryString)
      throws Exception {
    return newGson().fromJson(buildJsonRaw(path, additionalQueryString), typeOfT);
  }

  protected <T> T buildJson(String path, Type typeOfT) throws Exception {
    return buildJson(path, typeOfT, "");
  }

  private static Gson newGson() {
    return new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();
  }

  protected void assertNotFound(String path, String queryString) throws Exception {
    buildResponse(path, queryString, SC_NOT_FOUND);
  }
}
