// Copyright 2012 Google Inc. All Rights Reserved.
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

import org.eclipse.jgit.http.server.glue.MetaServlet;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;

import java.util.Enumeration;

import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * Servlet to serve Gitiles.
 * <p>
 * This servlet can either be constructed manually with its dependencies, or
 * configured to use default implementations for the Gitiles interfaces. To
 * configure the defaults, you must provide a single init parameter
 * "configPath", which is the path to a git config file containing additional
 * configuration.
 */
public class GitilesServlet extends MetaServlet {
  private static final long serialVersionUID = 1L;

  /** The prefix from which static resources are served. */
  public static final String STATIC_PREFIX = "/+static/";

  public GitilesServlet(
      Config config,
      @Nullable Renderer renderer,
      @Nullable GitilesUrls urls,
      @Nullable GitilesAccess.Factory accessFactory,
      @Nullable RepositoryResolver<HttpServletRequest> resolver,
      @Nullable VisibilityCache visibilityCache,
      @Nullable TimeCache timeCache,
      @Nullable GitwebRedirectFilter gitwebRedirect) {
    super(new GitilesFilter(
        config, renderer, urls, accessFactory, resolver, visibilityCache, timeCache,
        gitwebRedirect));
  }

  public GitilesServlet() {
    super(new GitilesFilter());
  }

  @Override
  protected GitilesFilter getDelegateFilter() {
    return (GitilesFilter) super.getDelegateFilter();
  }

  @Override
  public void init(final ServletConfig config) throws ServletException {
    getDelegateFilter().init(new FilterConfig() {
      @Override
      public String getFilterName() {
        return getDelegateFilter().getClass().getName();
      }

      @Override
      public String getInitParameter(String name) {
        return config.getInitParameter(name);
      }

      @SuppressWarnings("rawtypes")
      @Override
      public Enumeration getInitParameterNames() {
        return config.getInitParameterNames();
      }

      @Override
      public ServletContext getServletContext() {
        return config.getServletContext();
      }
    });
  }

  /**
   * Add a custom filter for a view.
   * <p>
   * Must be called before initializing the servlet.
   *
   * @param view view type.
   * @param filter filter.
   */
  public void addFilter(GitilesView.Type view, Filter filter) {
    getDelegateFilter().addFilter(view, filter);
  }

  /**
   * Set a custom handler for a view.
   * <p>
   * Must be called before initializing the servlet.
   *
   * @param view view type.
   * @param handler handler.
   */
  public void setHandler(GitilesView.Type view, HttpServlet handler) {
    getDelegateFilter().setHandler(view, handler);
  }

  public BaseServlet getDefaultHandler(GitilesView.Type view) {
    return getDelegateFilter().getDefaultHandler(view);
  }
}
