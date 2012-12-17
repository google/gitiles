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

import com.google.gitiles.GitilesServlet;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

class DevServer {
  private final Server httpd;

  DevServer(Config cfg) throws IOException {
    httpd = new Server();
    httpd.setConnectors(connectors(cfg));
    httpd.setThreadPool(threadPool(cfg));
    httpd.setHandler(handler());
  }

  void start() throws Exception {
    httpd.start();
    httpd.join();
  }

  private Connector[] connectors(Config cfg) {
    Connector c = new SelectChannelConnector();
    c.setHost(null);
    c.setPort(cfg.getInt("gitiles", null, "port", 8080));
    c.setStatsOn(false);
    return new Connector[]{c};
  }

  private ThreadPool threadPool(Config cfg) {
    QueuedThreadPool pool = new QueuedThreadPool();
    pool.setName("HTTP");
    pool.setMinThreads(2);
    pool.setMaxThreads(10);
    pool.setMaxQueued(50);
    return pool;
  }

  private Handler handler() throws IOException {
    ContextHandlerCollection handlers = new ContextHandlerCollection();
    handlers.addHandler(staticHandler());
    handlers.addHandler(appHandler());
    return handlers;

  }

  private Handler appHandler() {
    ServletContextHandler handler = new ServletContextHandler();
    handler.setContextPath("");
    handler.addServlet(new ServletHolder(new GitilesServlet()), "/*");
    return handler;
  }

  private FileNotFoundException badWebRoot(URL u) {
    return new FileNotFoundException("Cannot find web root from " + u);
  }

  private FileNotFoundException badWebRoot(URL u, Throwable cause) {
    FileNotFoundException notFound = badWebRoot(u);
    notFound.initCause(cause);
    return notFound;
  }

  private Handler staticHandler(URL targetUrl) throws IOException {
    if (!"file".equals(targetUrl.getProtocol())) {
      throw badWebRoot(targetUrl);
    }
    String targetPath = targetUrl.getPath();
    // targetPath is an arbitrary path under gitiles-dev/target in the standard
    // Maven package layout.
    int targetIndex = targetPath.lastIndexOf("gitiles-dev/target/");
    if (targetIndex < 0) {
      throw badWebRoot(targetUrl);
    }
    String staticPath = targetPath.substring(0, targetIndex)
        + "./gitiles-servlet/src/main/resources/com/google/gitiles/static";
    URI staticUri;
    try {
      staticUri = new URI("file", staticPath, null).normalize();
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
    File staticRoot = new File(staticUri);
    if (!staticRoot.exists() || !staticRoot.isDirectory()) {
      throw badWebRoot(targetUrl);
    }
    ResourceHandler rh = new ResourceHandler();
    try {
      rh.setBaseResource(new FileResource(staticUri.toURL()));
    } catch (URISyntaxException e) {
      throw badWebRoot(targetUrl, e);
    }
    rh.setWelcomeFiles(new String[]{});
    rh.setDirectoriesListed(false);
    ContextHandler handler = new ContextHandler("/+static");
    handler.setHandler(rh);
    return handler;
  }

  private Handler staticHandler() throws IOException {
    URL u = getClass().getResource(getClass().getSimpleName() + ".class");
    if (u == null) {
      throw new FileNotFoundException("Cannot find web root");
    }
    if ("jar".equals(u.getProtocol())) {
      int jarEntry = u.getPath().indexOf("!/");
      if (jarEntry < 0) {
        throw badWebRoot(u);
      }
      try {
        return staticHandler(new URL(u.getPath().substring(0, jarEntry)));
      } catch (MalformedURLException e) {
        throw badWebRoot(u, e);
      }
    } else {
      return staticHandler(u);
    }
  }
}
