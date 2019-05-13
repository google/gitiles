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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.gitiles.GitilesRequestFailureException.FailureReason;
import com.google.gson.reflect.TypeToken;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.restricted.NullData;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/** Serves the top level index page for a Gitiles host. */
public class HostIndexServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  protected final GitilesUrls urls;

  public HostIndexServlet(
      GitilesAccess.Factory accessFactory, Renderer renderer, GitilesUrls urls) {
    super(renderer, accessFactory);
    this.urls = checkNotNull(urls, "urls");
  }

  private Map<String, RepositoryDescription> list(
      HttpServletRequest req, HttpServletResponse res, String prefix, Set<String> branches)
      throws IOException {
    Map<String, RepositoryDescription> descs;
    try {
      descs = getAccess(req).listRepositories(prefix, branches);
    } catch (ServiceNotEnabledException e) {
      throw new GitilesRequestFailureException(FailureReason.SERVICE_NOT_ENABLED, e);
    } catch (ServiceNotAuthorizedException e) {
      throw new GitilesRequestFailureException(FailureReason.NOT_AUTHORIZED, e);
    }
    if (prefix != null && descs.isEmpty()) {
      throw new GitilesRequestFailureException(FailureReason.REPOSITORY_NOT_FOUND);
    }
    return descs;
  }

  private SoyMapData toSoyMapData(
      RepositoryDescription desc, @Nullable String prefix, GitilesView view) {
    return new SoyMapData(
        "name", stripPrefix(prefix, desc.name),
        "description", Strings.nullToEmpty(desc.description),
        "url", GitilesView.repositoryIndex().copyFrom(view).setRepositoryName(desc.name).toUrl());
  }

  @Override
  protected void doHead(HttpServletRequest req, HttpServletResponse res) throws IOException {
    Optional<FormatType> format = getFormat(req);
    if (!format.isPresent()) {
      throw new GitilesRequestFailureException(FailureReason.UNSUPPORTED_RESPONSE_FORMAT);
    }

    GitilesView view = ViewFilter.getView(req);
    String prefix = view.getRepositoryPrefix();
    if (prefix != null) {
      Map<String, RepositoryDescription> descs = list(req, res, prefix, Collections.emptySet());
      if (descs == null) {
        return;
      }
    }
    switch (format.get()) {
      case HTML:
      case JSON:
      case TEXT:
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType(format.get().getMimeType());
        break;
      case DEFAULT:
      default:
        throw new GitilesRequestFailureException(FailureReason.UNSUPPORTED_RESPONSE_FORMAT);
    }
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    String prefix = view.getRepositoryPrefix();
    Map<String, RepositoryDescription> descs = list(req, res, prefix, parseShowBranch(req));
    if (descs == null) {
      return;
    }

    SoyListData repos = new SoyListData();
    for (RepositoryDescription desc : descs.values()) {
      if (prefix == null || desc.name.startsWith(prefix)) {
        repos.add(toSoyMapData(desc, prefix, view));
      }
    }

    String hostName = urls.getHostName(req);
    List<Map<String, String>> breadcrumbs = null;
    if (prefix != null) {
      hostName = hostName + '/' + prefix;
      breadcrumbs = view.getBreadcrumbs();
    }
    renderHtml(
        req,
        res,
        "gitiles.hostIndex",
        ImmutableMap.of(
            "hostName",
            hostName,
            "breadcrumbs",
            breadcrumbs != null ? new SoyListData(breadcrumbs) : NullData.INSTANCE,
            "prefix",
            prefix != null ? prefix + '/' : "",
            "repositories",
            repos));
  }

  @Override
  protected void doGetText(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String prefix = ViewFilter.getView(req).getRepositoryPrefix();
    Set<String> branches = parseShowBranch(req);
    Map<String, RepositoryDescription> descs = list(req, res, prefix, branches);
    if (descs == null) {
      return;
    }

    try (Writer writer = startRenderText(req, res)) {
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
        writer.write(GitilesUrls.escapeName(stripPrefix(prefix, repo.name)));
        writer.write('\n');
      }
    }
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String prefix = ViewFilter.getView(req).getRepositoryPrefix();
    Map<String, RepositoryDescription> descs = list(req, res, prefix, parseShowBranch(req));
    if (descs == null) {
      return;
    }
    if (prefix != null) {
      Map<String, RepositoryDescription> r = new LinkedHashMap<>();
      for (Map.Entry<String, RepositoryDescription> e : descs.entrySet()) {
        r.put(stripPrefix(prefix, e.getKey()), e.getValue());
      }
      descs = r;
    }
    renderJson(req, res, descs, new TypeToken<Map<String, RepositoryDescription>>() {}.getType());
  }

  private static String stripPrefix(@Nullable String prefix, String name) {
    if (prefix != null && name.startsWith(prefix)) {
      return name.substring(prefix.length() + 1);
    }
    return name;
  }

  private static Set<String> parseShowBranch(HttpServletRequest req) {
    // Roughly match Gerrit Code Review's /projects/ API by supporting
    // both show-branch and b as query parameters.
    Set<String> branches = Sets.newLinkedHashSet();
    String[] values = req.getParameterValues("show-branch");
    if (values != null) {
      Collections.addAll(branches, values);
    }
    values = req.getParameterValues("b");
    if (values != null) {
      Collections.addAll(branches, values);
    }
    return branches;
  }
}
