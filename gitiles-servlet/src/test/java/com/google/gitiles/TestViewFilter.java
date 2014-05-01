// Copyright (C) 2014 Google Inc. All Rights Reserved.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gitiles.GitilesFilter.REPO_PATH_REGEX;
import static com.google.gitiles.GitilesFilter.REPO_REGEX;
import static com.google.gitiles.GitilesFilter.ROOT_REGEX;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;

import org.eclipse.jgit.http.server.glue.MetaFilter;
import org.eclipse.jgit.http.server.glue.MetaServlet;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.junit.TestRepository;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Run {@link ViewFilter} in a test environment. */
class TestViewFilter {
  static class Result {
    private final GitilesView view;
    private final FakeHttpServletResponse res;

    private Result(GitilesView view, FakeHttpServletResponse res) {
      this.view = view;
      this.res = res;
    }

    GitilesView getView() {
      return view;
    }

    FakeHttpServletResponse getResponse() {
      return res;
    }
  }

  static Result service(TestRepository<? extends DfsRepository> repo, String pathAndQuery)
      throws IOException, ServletException {
    TestServlet servlet = new TestServlet();
    ViewFilter vf = new ViewFilter(
        new TestGitilesAccess(repo.getRepository()),
        TestGitilesUrls.URLS,
        new VisibilityCache(false));
    MetaFilter mf = new MetaFilter();

    for (Pattern p : ImmutableList.of(ROOT_REGEX, REPO_REGEX, REPO_PATH_REGEX)) {
      mf.serveRegex(p)
          .through(vf)
          .with(servlet);
    }
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    dummyServlet(mf).service(newRequest(repo, pathAndQuery), res);
    if (servlet.view != null && servlet.view.getRepositoryName() != null) {
      assertEquals(repo.getRepository().getDescription().getRepositoryName(),
          servlet.view.getRepositoryName());
    }
    return new Result(servlet.view, res);
  }

  private static class TestServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private GitilesView view;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
      checkState(view == null);
      view = ViewFilter.getView(req);
    }
  }

  private static FakeHttpServletRequest newRequest(TestRepository<? extends DfsRepository> repo,
      String pathAndQuery) {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest(repo.getRepository());
    int q = pathAndQuery.indexOf('?');
    if (q > 0) {
      req.setPathInfo(pathAndQuery.substring(0, q));
      req.setQueryString(pathAndQuery.substring(q + 1));
    } else {
      req.setPathInfo(pathAndQuery);
    }
    return req;
  }

  private static MetaServlet dummyServlet(MetaFilter mf) {
    return new MetaServlet(mf) {
      private static final long serialVersionUID = 1L;
    };
  }

  private TestViewFilter() {
  }
}
