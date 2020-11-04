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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gitiles.GitilesServlet.STATIC_PREFIX;
import static com.google.gitiles.Renderer.fileUrlMapper;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.gitiles.blame.BlameServlet;
import com.google.gitiles.blame.cache.BlameCache;
import com.google.gitiles.blame.cache.BlameCacheImpl;
import com.google.gitiles.doc.DocServlet;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.http.server.glue.MetaFilter;
import org.eclipse.jgit.http.server.glue.ServletBinder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;

/**
 * MetaFilter to serve Gitiles.
 *
 * <p>Do not use directly; use {@link GitilesServlet}.
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
  static final Pattern ROOT_REGEX =
      Pattern.compile(
          ""
              + "^(      " // 1. Everything
              + "  /*    " // Excess slashes
              + "  (/)   " // 2. Repo name (just slash)
              + "  ()    " // 3. Command
              + "  ()    " // 4. Path
              + ")$      ",
          Pattern.COMMENTS);

  @VisibleForTesting
  static final Pattern REPO_REGEX =
      Pattern.compile(
          ""
              + "^(                     " // 1. Everything
              + "  /*                   " // Excess slashes
              + "  (                    " // 2. Repo name
              + "   /                   " // Leading slash
              + "   (?:.(?!             " // Anything, as long as it's not followed by...
              + "        /"
              + CMD
              + "/  " // the special "/<CMD>/" separator,
              + "        |/"
              + CMD
              + "$ " // or "/<CMD>" at the end of the string
              + "        ))*?           "
              + "  )                    "
              + "  /*                   " // Trailing slashes
              + "  ()                   " // 3. Command
              + "  ()                   " // 4. Path
              + ")$                     ",
          Pattern.COMMENTS);

  @VisibleForTesting
  static final Pattern REPO_PATH_REGEX =
      Pattern.compile(
          ""
              + "^(              " // 1. Everything
              + "  /*            " // Excess slashes
              + "  (             " // 2. Repo name
              + "   /            " // Leading slash
              + "   .*?          " // Anything, non-greedy
              + "  )             "
              + "  /("
              + CMD
              + ")" // 3. Command
              + "  (             " // 4. Path
              + "   (?:/.*)?     " // Slash path, or nothing.
              + "  )             "
              + ")$              ",
          Pattern.COMMENTS);

  private static class DispatchFilter extends AbstractHttpFilter {
    private final ListMultimap<GitilesView.Type, Filter> filters;
    private final Map<GitilesView.Type, HttpServlet> servlets;

    private DispatchFilter(
        ListMultimap<GitilesView.Type, Filter> filters,
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

  private Config config;
  private Renderer renderer;
  private GitilesUrls urls;
  private Linkifier linkifier;
  private GitilesAccess.Factory accessFactory;
  private RepositoryResolver<HttpServletRequest> resolver;
  private VisibilityCache visibilityCache;
  private TimeCache timeCache;
  private BlameCache blameCache;
  private GitwebRedirectFilter gitwebRedirect;
  private Filter errorHandler;
  private boolean initialized;

  GitilesFilter() {}

  GitilesFilter(
      Config config,
      @Nullable Renderer renderer,
      @Nullable GitilesUrls urls,
      @Nullable GitilesAccess.Factory accessFactory,
      @Nullable RepositoryResolver<HttpServletRequest> resolver,
      @Nullable VisibilityCache visibilityCache,
      @Nullable TimeCache timeCache,
      @Nullable BlameCache blameCache,
      @Nullable GitwebRedirectFilter gitwebRedirect,
      @Nullable Filter errorHandler) {
    this.config = checkNotNull(config, "config");
    this.renderer = renderer;
    this.urls = urls;
    this.accessFactory = accessFactory;
    this.visibilityCache = visibilityCache;
    this.timeCache = timeCache;
    this.blameCache = blameCache;
    this.gitwebRedirect = gitwebRedirect;
    if (resolver != null) {
      this.resolver = resolver;
    }
    this.errorHandler = errorHandler;
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

    ServletBinder root = serveRegex(ROOT_REGEX).through(viewFilter);
    if (gitwebRedirect != null) {
      root.through(gitwebRedirect);
    }
    root.through(dispatchFilter);

    serveRegex(REPO_REGEX).through(repositoryFilter).through(viewFilter).through(dispatchFilter);

    serveRegex(REPO_PATH_REGEX)
        .through(repositoryFilter)
        .through(viewFilter)
        .through(dispatchFilter);

    initialized = true;
  }

  @Override
  protected ServletBinder register(ServletBinder b) {
    b.through(errorHandler);
    return b;
  }

  public synchronized BaseServlet getDefaultHandler(GitilesView.Type view) {
    checkNotInitialized();
    switch (view) {
      case HOST_INDEX:
        return new HostIndexServlet(accessFactory, renderer, urls);
      case REPOSITORY_INDEX:
        return new RepositoryIndexServlet(accessFactory, renderer, timeCache);
      case REFS:
        return new RefServlet(accessFactory, renderer, timeCache);
      case REVISION:
        return new RevisionServlet(accessFactory, renderer, linkifier());
      case SHOW:
      case PATH:
        return new PathServlet(accessFactory, renderer, urls);
      case DIFF:
        return new DiffServlet(accessFactory, renderer, linkifier());
      case LOG:
        return new LogServlet(accessFactory, renderer, linkifier());
      case DESCRIBE:
        return new DescribeServlet(accessFactory);
      case ARCHIVE:
        return new ArchiveServlet(accessFactory);
      case BLAME:
        return new BlameServlet(accessFactory, renderer, blameCache);
      case DOC:
      case ROOTED_DOC:
        return new DocServlet(accessFactory, renderer);
      default:
        throw new IllegalArgumentException("Invalid view type: " + view);
    }
  }

  public synchronized void setRenderer(Renderer renderer) {
    checkNotInitialized();
    this.renderer = checkNotNull(renderer, "renderer");
  }

  synchronized void addFilter(GitilesView.Type view, Filter filter) {
    checkNotInitialized();
    filters.put(checkNotNull(view, "view"), checkNotNull(filter, "filter for %s", view));
  }

  synchronized void setHandler(GitilesView.Type view, HttpServlet handler) {
    checkNotInitialized();
    servlets.put(checkNotNull(view, "view"), checkNotNull(handler, "handler for %s", view));
  }

  private synchronized void checkNotInitialized() {
    checkState(!initialized, "Gitiles already initialized");
  }

  private synchronized Linkifier linkifier() {
    if (linkifier == null) {
      checkState(urls != null, "GitilesUrls not yet set");
      linkifier = new Linkifier(urls, config);
    }
    return linkifier;
  }

  private void setDefaultFields(FilterConfig filterConfig) throws ServletException {
    setDefaultConfig(filterConfig);
    setDefaultRenderer(filterConfig);
    setDefaultUrls();
    setDefaultAccess();
    setDefaultVisibilityCache();
    setDefaultTimeCache();
    setDefaultBlameCache();
    setDefaultGitwebRedirect();
    setDefaultErrorHandler();
  }

  private void setDefaultConfig(FilterConfig filterConfig) throws ServletException {
    if (config == null) {
      try {
        config = GitilesConfig.loadDefault(filterConfig);
      } catch (IOException | ConfigInvalidException e) {
        throw new ServletException(e);
      }
    }
  }

  private void setDefaultRenderer(FilterConfig filterConfig) {
    if (renderer == null) {
      renderer =
          new DefaultRenderer(
              filterConfig.getServletContext().getContextPath() + STATIC_PREFIX,
              Arrays.stream(config.getStringList("gitiles", null, "customTemplates"))
                  .map(fileUrlMapper())
                  .collect(toList()),
              firstNonNull(config.getString("gitiles", null, "siteTitle"), "Gitiles"));
    }
  }

  private void setDefaultUrls() throws ServletException {
    if (urls == null) {
      try {
        urls =
            new DefaultUrls(
                config.getString("gitiles", null, "canonicalHostName"),
                getBaseGitUrl(config),
                config.getString("gitiles", null, "gerritUrl"));
      } catch (UnknownHostException e) {
        throw new ServletException(e);
      }
    }
  }

  private void setDefaultAccess() throws ServletException {
    if (accessFactory == null || resolver == null) {
      String basePath = config.getString("gitiles", null, "basePath");
      if (basePath == null) {
        throw new ServletException("gitiles.basePath not set");
      }
      boolean exportAll = config.getBoolean("gitiles", null, "exportAll", false);

      FileResolver<HttpServletRequest> fileResolver;
      if (resolver == null) {
        fileResolver = new FileResolver<>(new File(basePath), exportAll);
        resolver = fileResolver;
      } else if (resolver instanceof FileResolver) {
        fileResolver = (FileResolver<HttpServletRequest>) resolver;
      } else {
        fileResolver = null;
      }
      if (accessFactory == null) {
        checkState(fileResolver != null, "need a FileResolver when GitilesAccess.Factory not set");
        try {
          accessFactory =
              new DefaultAccess.Factory(
                  new File(basePath), getBaseGitUrl(config), config, fileResolver);
        } catch (IOException e) {
          throw new ServletException(e);
        }
      }
    }
  }

  private void setDefaultVisibilityCache() {
    if (visibilityCache == null) {
      if (config.getSubsections("cache").contains("visibility")) {
        visibilityCache = new VisibilityCache(ConfigUtil.getCacheBuilder(config, "visibility"));
      } else {
        visibilityCache = new VisibilityCache();
      }
    }
  }

  private void setDefaultTimeCache() {
    if (timeCache == null) {
      if (config.getSubsections("cache").contains("tagTime")) {
        timeCache = new TimeCache(ConfigUtil.getCacheBuilder(config, "tagTime"));
      } else {
        timeCache = new TimeCache();
      }
    }
  }

  private void setDefaultBlameCache() {
    if (blameCache == null) {
      if (config.getSubsections("cache").contains("blame")) {
        blameCache = new BlameCacheImpl(ConfigUtil.getCacheBuilder(config, "blame"));
      } else {
        blameCache = new BlameCacheImpl();
      }
    }
  }

  private void setDefaultGitwebRedirect() {
    if (gitwebRedirect == null) {
      if (config.getBoolean("gitiles", null, "redirectGitweb", true)) {
        gitwebRedirect = new GitwebRedirectFilter();
      }
    }
  }

  private void setDefaultErrorHandler() {
    if (errorHandler == null) {
      errorHandler = new DefaultErrorHandlingFilter(renderer);
    }
  }

  private static String getBaseGitUrl(Config config) throws ServletException {
    String baseGitUrl = config.getString("gitiles", null, "baseGitUrl");
    if (baseGitUrl == null) {
      throw new ServletException("gitiles.baseGitUrl not set");
    }
    return baseGitUrl;
  }
}
