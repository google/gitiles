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
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
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
import java.io.Writer;
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

  public HostIndexServlet(GitilesAccess.Factory accessFactory, Renderer renderer,
      GitilesUrls urls) {
    super(renderer, accessFactory);
    this.urls = checkNotNull(urls, "urls");
  }

  private Map<String, RepositoryDescription> getDescriptions(HttpServletRequest req,
      HttpServletResponse res) throws IOException {
    return getDescriptions(req, res, parseShowBranch(req));
  }

  private Map<String, RepositoryDescription> getDescriptions(HttpServletRequest req,
      HttpServletResponse res, Set<String> branches) throws IOException {
    Map<String, RepositoryDescription> descs;
    try {
      descs = getAccess(req).listRepositories(branches);
    } catch (RepositoryNotFoundException e) {
      res.sendError(SC_NOT_FOUND);
      return null;
    } catch (ServiceNotEnabledException e) {
      res.sendError(SC_FORBIDDEN);
      return null;
    } catch (ServiceNotAuthorizedException e) {
      res.sendError(SC_UNAUTHORIZED);
      return null;
    } catch (ServiceMayNotContinueException e) {
      // TODO(dborowitz): Show the error message to the user.
      res.sendError(SC_FORBIDDEN);
      return null;
    } catch (IOException err) {
      String name = urls.getHostName(req);
      log.warn("Cannot scan repositories" + (name != null ? " for " + name : ""), err);
      res.sendError(SC_SERVICE_UNAVAILABLE);
      return null;
    }
    return descs;
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

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    Map<String, RepositoryDescription> descs = getDescriptions(req, res);
    if (descs == null) {
      return;
    }
    SoyListData repos = new SoyListData();
    for (RepositoryDescription desc : descs.values()) {
      repos.add(toSoyMapData(desc, ViewFilter.getView(req)));
    }

    renderHtml(req, res, "gitiles.hostIndex", ImmutableMap.of(
        "hostName", urls.getHostName(req),
        "baseUrl", urls.getBaseGitUrl(req),
        "repositories", repos));
  }

  @Override
  protected void doGetText(HttpServletRequest req, HttpServletResponse res) throws IOException {
    Set<String> branches = parseShowBranch(req);
    Map<String, RepositoryDescription> descs = getDescriptions(req, res, branches);
    if (descs == null) {
      return;
    }

    Writer writer = startRenderText(req, res);
    for (RepositoryDescription repo : descs.values()) {
      for (String name : branches) {
        String ref = repo.branches.get(name);
        if (ref == null) {
          // Print stub (forty '-' symbols)
          ref = "----------------------------------------";
        }
        writer.write(ref);
        writer.write(' ');
      }
      writer.write(GitilesUrls.NAME_ESCAPER.apply(repo.name));
      writer.write('\n');
    }
    writer.flush();
    writer.close();
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    Map<String, RepositoryDescription> descs = getDescriptions(req, res);
    if (descs == null) {
      return;
    }
    renderJson(req, res, descs, new TypeToken<Map<String, RepositoryDescription>>() {}.getType());
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
