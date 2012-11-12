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
import static com.google.gitiles.FormatType.JSON;
import static com.google.gitiles.FormatType.TEXT;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves the top level index page for a Gitiles host. */
public class HostIndexServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(HostIndexServlet.class);

  protected final GitilesUrls urls;
  private final GitilesAccess.Factory accessFactory;

  public HostIndexServlet(Renderer renderer, GitilesUrls urls,
      GitilesAccess.Factory accessFactory) {
    super(renderer);
    this.urls = checkNotNull(urls, "urls");
    this.accessFactory = checkNotNull(accessFactory, "accessFactory");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    FormatType format;
    try {
      format = FormatType.getFormatType(req);
    } catch (IllegalArgumentException err) {
      res.sendError(SC_BAD_REQUEST);
      return;
    }

    Set<String> branches = parseShowBranch(req);
    Map<String, RepositoryDescription> descs;
    try {
      descs = accessFactory.forRequest(req).listRepositories(branches);
    } catch (RepositoryNotFoundException e) {
      res.sendError(SC_NOT_FOUND);
      return;
    } catch (ServiceNotEnabledException e) {
      res.sendError(SC_FORBIDDEN);
      return;
    } catch (ServiceNotAuthorizedException e) {
      res.sendError(SC_UNAUTHORIZED);
      return;
    } catch (ServiceMayNotContinueException e) {
      // TODO(dborowitz): Show the error message to the user.
      res.sendError(SC_FORBIDDEN);
      return;
    } catch (IOException err) {
      String name = urls.getHostName(req);
      log.warn("Cannot scan repositories" + (name != null ? "for " + name : ""), err);
      res.sendError(SC_SERVICE_UNAVAILABLE);
      return;
    }

    switch (format) {
      case HTML:
      case DEFAULT:
      default:
        displayHtml(req, res, descs);
        break;

      case TEXT:
        displayText(req, res, branches, descs);
        break;

      case JSON:
        displayJson(req, res, descs);
        break;
    }
  }

  private SoyMapData toSoyMapData(RepositoryDescription desc, GitilesView view) {
    return new SoyMapData(
        "name", desc.name,
        "description", Strings.nullToEmpty(desc.description),
        "url", GitilesView.repositoryIndex()
            .copyFrom(view)
            .setRepositoryName(desc.name)
            .toUrl());
  }

  private void displayHtml(HttpServletRequest req, HttpServletResponse res,
      Map<String, RepositoryDescription> descs) throws IOException {
    SoyListData repos = new SoyListData();
    for (RepositoryDescription desc : descs.values()) {
      repos.add(toSoyMapData(desc, ViewFilter.getView(req)));
    }

    render(req, res, "gitiles.hostIndex", ImmutableMap.of(
        "hostName", urls.getHostName(req),
        "baseUrl", urls.getBaseGitUrl(req),
        "repositories", repos));
  }

  private void displayText(HttpServletRequest req, HttpServletResponse res,
      Set<String> branches, Map<String, RepositoryDescription> descs) throws IOException {
    res.setContentType(TEXT.getMimeType());
    res.setCharacterEncoding("UTF-8");
    res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment");
    setNotCacheable(res);

    PrintWriter writer = res.getWriter();
    for (RepositoryDescription repo : descs.values()) {
      for (String name : branches) {
        String ref = repo.branches.get(name);
        if (ref == null) {
          // Print stub (forty '-' symbols)
          ref = "----------------------------------------";
        }
        writer.print(ref);
        writer.print(' ');
      }
      writer.print(GitilesUrls.NAME_ESCAPER.apply(repo.name));
      writer.print('\n');
    }
    writer.flush();
    writer.close();
  }

  private void displayJson(HttpServletRequest req, HttpServletResponse res,
      Map<String, RepositoryDescription> descs) throws IOException {
    res.setContentType(JSON.getMimeType());
    res.setCharacterEncoding("UTF-8");
    res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment");
    setNotCacheable(res);

    PrintWriter writer = res.getWriter();
    new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setPrettyPrinting()
        .generateNonExecutableJson()
        .create()
        .toJson(descs,
          new TypeToken<Map<String, RepositoryDescription>>() {}.getType(),
          writer);
    writer.print('\n');
    writer.close();
  }

  private static Set<String> parseShowBranch(HttpServletRequest req) {
    // Roughly match Gerrit Code Review's /projects/ API by supporting
    // both show-branch and b as query parameters.
    Set<String> branches = Sets.newLinkedHashSet();
    String[] values = req.getParameterValues("show-branch");
    if (values != null) {
      branches.addAll(Arrays.asList(values));
    }
    values = req.getParameterValues("b");
    if (values != null) {
      branches.addAll(Arrays.asList(values));
    }
    return branches;
  }
}
