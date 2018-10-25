// Copyright 2012 Google Inc. All Rights Reserved.
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

import com.google.common.collect.ImmutableList;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;

/** Static utility methods for creating {@link GitilesServlet}s for testing. */
public class TestGitilesServlet {
  /** @see #create(TestRepository) */
  public static GitilesServlet create(final TestRepository<DfsRepository> repo)
      throws ServletException {
    return create(repo, new GitwebRedirectFilter());
  }

  /** @see #create(TestRepository) */
  public static GitilesServlet create(
      final TestRepository<DfsRepository> repo, GitwebRedirectFilter gitwebRedirect)
      throws ServletException {
    return create(repo, gitwebRedirect, null);
  }
  /**
   * Create a servlet backed by a single test repository.
   *
   * <p>The servlet uses the same filter lists as a real servlet, but only knows about a single
   * repo, having the name returned by {@link
   * org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription#getRepositoryName()}. Pass a
   * {@link FakeHttpServletRequest} and {@link FakeHttpServletResponse} to the servlet's {@code
   * service} method to test.
   *
   * @param repo the test repo backing the servlet.
   * @param gitwebRedirect optional redirect filter for gitweb URLs.
   * @param config optional custom gitiles config
   * @return a servlet.
   */
  public static GitilesServlet create(
      final TestRepository<DfsRepository> repo, GitwebRedirectFilter gitwebRedirect, Config config)
      throws ServletException {
    final String repoName = repo.getRepository().getDescription().getRepositoryName();

    GitilesAccess.Factory accessFactory =
        (config == null)
            ? new TestGitilesAccess(repo.getRepository())
            : new TestGitilesAccess(repo.getRepository(), config);

    GitilesServlet servlet =
        new GitilesServlet(
            new Config(),
            new DefaultRenderer(
                GitilesServlet.STATIC_PREFIX, ImmutableList.<URL>of(), repoName + " test site"),
            TestGitilesUrls.URLS,
            accessFactory,
            new RepositoryResolver<HttpServletRequest>() {
              @Override
              public Repository open(HttpServletRequest req, String name)
                  throws RepositoryNotFoundException {
                if (!repoName.equals(name)) {
                  throw new RepositoryNotFoundException(name);
                }
                return repo.getRepository();
              }
            },
            null,
            null,
            null,
            gitwebRedirect);

    servlet.init(
        new ServletConfig() {
          @Override
          public String getInitParameter(String name) {
            return null;
          }

          @Override
          public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(ImmutableList.<String>of());
          }

          @Override
          public ServletContext getServletContext() {
            return null;
          }

          @Override
          public String getServletName() {
            return TestGitilesServlet.class.getName();
          }
        });
    return servlet;
  }

  private TestGitilesServlet() {}
}
