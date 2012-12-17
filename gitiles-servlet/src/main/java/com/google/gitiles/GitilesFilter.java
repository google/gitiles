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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gitiles.GitilesServlet.STATIC_PREFIX;
import static com.google.gitiles.ViewFilter.getRegexGroup;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.RepositoryFilter;
import org.eclipse.jgit.http.server.glue.MetaFilter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * MetaFilter to serve Gitiles.
 * <p>
 * Do not use directly; use {@link GitilesServlet}.
 */
class GitilesFilter extends MetaFilter {

  // The following regexes have the following capture groups:
  // 1. The whole string, which causes RegexPipeline to set REGEX_GROUPS but
  //    not otherwise modify the original request.
  // 2. The repository name part, before /<CMD>.
  // 3. The command, <CMD>, with no slashes and beginning with +. Commands have
  //    names analogous to (but not exactly the same as) git command names, such
  //    as "+log" and "+show". The bare command "+" maps to one of the other
  //    commands based on the revision/path, and may change over time.
  // 4. The revision/path part, after /<CMD> (called just "path" below). This is
  //    split into a revision and a path by RevisionParser.

  private static final String CMD = "\\+[a-z0-9-]*";

  @VisibleForTesting
  static final Pattern ROOT_REGEX = Pattern.compile(""
      + "^(      " // 1. Everything
      + "  /*    " // Excess slashes
      + "  (/)   " // 2. Repo name (just slash)
      + "  ()    " // 3. Command
      + "  ()    " // 4. Path
      + ")$      ",
      Pattern.COMMENTS);

  @VisibleForTesting
  static final Pattern REPO_REGEX = Pattern.compile(""
      + "^(                     " // 1. Everything
      + "  /*                   " // Excess slashes
      + "  (                    " // 2. Repo name
      + "   /                   " // Leading slash
      + "   (?:.(?!             " // Anything, as long as it's not followed by...
      + "        /" + CMD + "/  " // the special "/<CMD>/" separator,
      + "        |/" + CMD + "$ " // or "/<CMD>" at the end of the string
      + "        ))*?           "
      + "  )                    "
      + "  /*                   " // Trailing slashes
      + "  ()                   " // 3. Command
      + "  ()                   " // 4. Path
      + ")$                     ",
      Pattern.COMMENTS);

  @VisibleForTesting
  static final Pattern REPO_PATH_REGEX = Pattern.compile(""
      + "^(              " // 1. Everything
      + "  /*            " // Excess slashes
      + "  (             " // 2. Repo name
      + "   /            " // Leading slash
      + "   .*?          " // Anything, non-greedy
      + "  )             "
      + "  /(" + CMD + ")" // 3. Command
      + "  (             " // 4. Path
      + "   (?:/.*)?     " // Slash path, or nothing.
      + "  )             "
      + ")$              ",
      Pattern.COMMENTS);

  private static class DispatchFilter extends AbstractHttpFilter {
    private final ListMultimap<GitilesView.Type, Filter> filters;
    private final Map<GitilesView.Type, HttpServlet> servlets;

    private DispatchFilter(ListMultimap<GitilesView.Type, Filter> filters,
        Map<GitilesView.Type, HttpServlet> servlets) {
      this.filters = LinkedListMultimap.create(filters);
      this.servlets = ImmutableMap.copyOf(servlets);
      for (GitilesView.Type type : GitilesView.Type.values()) {
        checkState(servlets.containsKey(type), "Missing handler for view %s", type);
      }
    }

    @Override
    public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws IOException, ServletException {
      GitilesView view = checkNotNull(ViewFilter.getView(req));
      final Iterator<Filter> itr = filters.get(view.getType()).iterator();
      final HttpServlet servlet = servlets.get(view.getType());
      new FilterChain() {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res)
            throws IOException, ServletException {
          if (itr.hasNext()) {
            itr.next().doFilter(req, res, this);
          } else {
            servlet.service(req, res);
          }
        }
      }.doFilter(req, res);
    }
  }

  private final ListMultimap<GitilesView.Type, Filter> filters = LinkedListMultimap.create();
  private final Map<GitilesView.Type, HttpServlet> servlets = Maps.newHashMap();

  private Renderer renderer;
  private GitilesUrls urls;
  private Linkifier linkifier;
  private GitilesAccess.Factory accessFactory;
  private RepositoryResolver<HttpServletRequest> resolver;
  private VisibilityCache visibilityCache;
  private boolean initialized;

  GitilesFilter() {
  }

  GitilesFilter(
      Renderer renderer,
      GitilesUrls urls,
      GitilesAccess.Factory accessFactory,
      final RepositoryResolver<HttpServletRequest> resolver,
      VisibilityCache visibilityCache) {
    this.renderer = checkNotNull(renderer, "renderer");
    this.urls = checkNotNull(urls, "urls");
    this.accessFactory = checkNotNull(accessFactory, "accessFactory");
    this.visibilityCache = checkNotNull(visibilityCache, "visibilityCache");
    this.linkifier = new Linkifier(urls);
    this.resolver = wrapResolver(resolver);
  }

  @Override
  public synchronized void init(FilterConfig config) throws ServletException {
    super.init(config);
    setDefaultFields(config);

    for (GitilesView.Type type : GitilesView.Type.values()) {
      if (!servlets.containsKey(type)) {
        servlets.put(type, getDefaultHandler(type));
      }
    }

    Filter repositoryFilter = new RepositoryFilter(resolver);
    Filter viewFilter = new ViewFilter(accessFactory, urls, visibilityCache);
    Filter dispatchFilter = new DispatchFilter(filters, servlets);

    serveRegex(ROOT_REGEX)
        .through(viewFilter)
        .through(dispatchFilter);

    serveRegex(REPO_REGEX)
        .through(repositoryFilter)
        .through(viewFilter)
        .through(dispatchFilter);

    serveRegex(REPO_PATH_REGEX)
        .through(repositoryFilter)
        .through(viewFilter)
        .through(dispatchFilter);

    initialized = true;
  }

  public synchronized BaseServlet getDefaultHandler(GitilesView.Type view) {
    checkNotInitialized();
    switch (view) {
      case HOST_INDEX:
        return new HostIndexServlet(renderer, urls, accessFactory);
      case REPOSITORY_INDEX:
        return new RepositoryIndexServlet(renderer, accessFactory);
      case REVISION:
        return new RevisionServlet(renderer, linkifier);
      case PATH:
        return new PathServlet(renderer);
      case DIFF:
        return new DiffServlet(renderer, linkifier);
      case LOG:
        return new LogServlet(renderer, linkifier);
      default:
        throw new IllegalArgumentException("Invalid view type: " + view);
    }
  }

  synchronized void addFilter(GitilesView.Type view, Filter filter) {
    checkNotInitialized();
    filters.put(checkNotNull(view, "view"), checkNotNull(filter, "filter for %s", view));
  }

  synchronized void setHandler(GitilesView.Type view, HttpServlet handler) {
    checkNotInitialized();
    servlets.put(checkNotNull(view, "view"),
        checkNotNull(handler, "handler for %s", view));
  }

  private synchronized void checkNotInitialized() {
    checkState(!initialized, "Gitiles already initialized");
  }

  private static RepositoryResolver<HttpServletRequest> wrapResolver(
      final RepositoryResolver<HttpServletRequest> resolver) {
    checkNotNull(resolver, "resolver");
    return new RepositoryResolver<HttpServletRequest>() {
      @Override
      public Repository open(HttpServletRequest req, String name)
          throws RepositoryNotFoundException, ServiceNotAuthorizedException,
          ServiceNotEnabledException, ServiceMayNotContinueException {
        return resolver.open(req, ViewFilter.trimLeadingSlash(getRegexGroup(req, 1)));
      }
    };
  }

  private void setDefaultFields(FilterConfig filterConfig) throws ServletException {
    if (renderer != null && urls != null && accessFactory != null && resolver != null
        && visibilityCache != null) {
      return;
    }
    Config config;
    try {
      config = GitilesConfig.loadDefault(filterConfig);
    } catch (IOException e) {
      throw new ServletException(e);
    } catch (ConfigInvalidException e) {
      throw new ServletException(e);
    }

    if (renderer == null) {
      String staticPrefix = filterConfig.getServletContext().getContextPath() + STATIC_PREFIX;
      String customTemplates = config.getString("gitiles", null, "customTemplates");
      String siteTitle = Objects.firstNonNull(config.getString("gitiles", null, "siteTitle"),
          "Gitiles");
      // TODO(dborowitz): Automatically set to true when run with mvn jetty:run.
      if (config.getBoolean("gitiles", null, "reloadTemplates", false)) {
        renderer = new DebugRenderer(staticPrefix, customTemplates,
            Joiner.on(File.separatorChar).join(System.getProperty("user.dir"),
                "gitiles-servlet", "src", "main", "resources",
                "com", "google", "gitiles", "templates"), siteTitle);
      } else {
        renderer = new DefaultRenderer(staticPrefix, Renderer.toFileURL(customTemplates),
            siteTitle);
      }
    }
    if (urls == null) {
      try {
        urls = new DefaultUrls(
            config.getString("gitiles", null, "canonicalHostName"),
            getBaseGitUrl(config),
            getGerritUrl(config));
      } catch (UnknownHostException e) {
        throw new ServletException(e);
      }
    }
    linkifier = new Linkifier(urls);
    if (accessFactory == null || resolver == null) {
      String basePath = config.getString("gitiles", null, "basePath");
      if (basePath == null) {
        throw new ServletException("gitiles.basePath not set");
      }
      boolean exportAll = config.getBoolean("gitiles", null, "exportAll", false);

      FileResolver<HttpServletRequest> fileResolver;
      if (resolver == null) {
        fileResolver = new FileResolver<HttpServletRequest>(new File(basePath), exportAll);
        resolver = wrapResolver(fileResolver);
      } else if (resolver instanceof FileResolver) {
        fileResolver = (FileResolver<HttpServletRequest>) resolver;
      } else {
        fileResolver = null;
      }
      if (accessFactory == null) {
        checkState(fileResolver != null, "need a FileResolver when GitilesAccess.Factory not set");
        try {
        accessFactory = new DefaultAccess.Factory(
            new File(basePath),
            getBaseGitUrl(config),
            fileResolver);
        } catch (IOException e) {
          throw new ServletException(e);
        }
      }
    }
    if (visibilityCache == null) {
      if (config.getSubsections("cache").contains("visibility")) {
        visibilityCache =
            new VisibilityCache(false, ConfigUtil.getCacheBuilder(config, "visibility"));
      } else {
        visibilityCache = new VisibilityCache(false);
      }
    }
  }

  private static String getBaseGitUrl(Config config) throws ServletException {
    String baseGitUrl = config.getString("gitiles", null, "baseGitUrl");
    if (baseGitUrl == null) {
      throw new ServletException("gitiles.baseGitUrl not set");
    }
    return baseGitUrl;
  }

  private static String getGerritUrl(Config config) throws ServletException {
    String gerritUrl = config.getString("gitiles", null, "gerritUrl");
    if (gerritUrl == null) {
      throw new ServletException("gitiles.gerritUrl not set");
    }
    return gerritUrl;
  }
}
