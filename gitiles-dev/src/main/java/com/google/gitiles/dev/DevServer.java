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

package com.google.gitiles.dev;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gitiles.GitilesServlet.STATIC_PREFIX;

import com.google.common.base.Strings;
import com.google.gitiles.DebugRenderer;
import com.google.gitiles.GitilesAccess;
import com.google.gitiles.GitilesServlet;
import com.google.gitiles.PathServlet;
import com.google.gitiles.RepositoryDescription;
import com.google.gitiles.RootedDocServlet;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DevServer {
  private static final Logger log = LoggerFactory.getLogger(PathServlet.class);

  private static Config defaultConfig() {
    Config cfg = new Config();
    String cwd = System.getProperty("user.dir");
    cfg.setString("gitiles", null, "basePath", cwd);
    cfg.setBoolean("gitiles", null, "exportAll", true);
    cfg.setString("gitiles", null, "baseGitUrl", "file://" + cwd + "/");
    String networkHostName;
    try {
      networkHostName = InetAddress.getLocalHost().getCanonicalHostName();
    } catch (UnknownHostException e) {
      networkHostName = "127.0.0.1";
    }
    cfg.setString(
        "gitiles", null, "siteTitle", String.format("Gitiles - %s:%s", networkHostName, cwd));
    cfg.setString("gitiles", null, "canonicalHostName", new File(cwd).getName());
    return cfg;
  }

  private static Path findSourceRoot() throws IOException {
    String prop = "com.google.gitiles.sourcePath";
    String sourceRoot = System.getProperty(prop);
    if (sourceRoot == null) {
      throw new NoSuchFileException(
          String.format("Must set system property %s to top of source directory", prop));
    }
    return Paths.get(sourceRoot);
  }

  private final Path sourceRoot;
  private final Config cfg;
  private final Server httpd;

  DevServer(File cfgFile) throws IOException, ConfigInvalidException {
    // Jetty doesn't doesn't allow symlinks, so canonicalize.
    sourceRoot = findSourceRoot().toRealPath();

    Config cfg = defaultConfig();
    if (cfgFile.exists() && cfgFile.isFile()) {
      FileBasedConfig fcfg = new FileBasedConfig(cfg, cfgFile, FS.DETECTED);
      fcfg.load();
      cfg = fcfg;
    } else {
      log.info("Config file {} not found, using defaults", cfgFile.getPath());
    }
    this.cfg = cfg;

    httpd = new Server(cfg.getInt("gitiles", null, "port", 8080));
    httpd.setHandler(handler());
  }

  void start() throws Exception {
    httpd.start();
    httpd.join();
  }

  private Handler handler() throws IOException {
    ContextHandlerCollection handlers = new ContextHandlerCollection();
    handlers.addHandler(staticHandler());
    handlers.addHandler(appHandler());
    return handlers;
  }

  private Handler appHandler() {
    DebugRenderer renderer =
        new DebugRenderer(
            STATIC_PREFIX,
            Arrays.asList(cfg.getStringList("gitiles", null, "customTemplates")),
            sourceRoot
                .resolve("gitiles-servlet/src/main/resources/com/google/gitiles/templates")
                .toString(),
            firstNonNull(cfg.getString("gitiles", null, "siteTitle"), "Gitiles"));

    String docRoot = cfg.getString("gitiles", null, "docroot");
    Servlet servlet;
    if (!Strings.isNullOrEmpty(docRoot)) {
      servlet = createRootedDocServlet(renderer, docRoot);
    } else {
      servlet = new GitilesServlet(cfg, renderer, null, null, null, null, null, null, null);
    }

    ServletContextHandler handler = new ServletContextHandler();
    handler.setContextPath("");
    handler.addServlet(new ServletHolder(servlet), "/*");
    return handler;
  }

  private Handler staticHandler() throws IOException {
    Path staticRoot =
        sourceRoot.resolve("gitiles-servlet/src/main/resources/com/google/gitiles/static");
    ResourceHandler rh = new ResourceHandler();
    try {
      rh.setBaseResource(new PathResource(staticRoot.toUri().toURL()));
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
    rh.setWelcomeFiles(new String[] {});
    rh.setDirectoriesListed(false);
    ContextHandler handler = new ContextHandler("/+static");
    handler.setHandler(rh);
    return handler;
  }

  private Servlet createRootedDocServlet(DebugRenderer renderer, String docRoot) {
    File docRepo = new File(docRoot);
    FileKey repoKey = FileKey.exact(docRepo, FS.DETECTED);

    RepositoryResolver<HttpServletRequest> resolver =
        (req, name) -> {
          try {
            return RepositoryCache.open(repoKey, true);
          } catch (IOException e) {
            throw new RepositoryNotFoundException(repoKey.getFile(), e);
          }
        };

    return new RootedDocServlet(resolver, new RootedDocAccess(docRepo), renderer);
  }

  private class RootedDocAccess implements GitilesAccess.Factory {
    private final String repoName;

    RootedDocAccess(File docRepo) {
      if (Constants.DOT_GIT.equals(docRepo.getName())) {
        repoName = docRepo.getParentFile().getName();
      } else {
        repoName = docRepo.getName();
      }
    }

    @Override
    public GitilesAccess forRequest(HttpServletRequest req) {
      return new GitilesAccess() {
        @Override
        public Map<String, RepositoryDescription> listRepositories(
            String prefix, Set<String> branches) {
          return Collections.emptyMap();
        }

        @Override
        public Object getUserKey() {
          return null;
        }

        @Override
        public String getRepositoryName() {
          return repoName;
        }

        @Override
        public RepositoryDescription getRepositoryDescription() {
          RepositoryDescription d = new RepositoryDescription();
          d.name = getRepositoryName();
          return d;
        }

        @Override
        public Config getConfig() {
          return cfg;
        }
      };
    }
  }
}
