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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.http.server.glue.WrappedRequest;

import java.io.IOException;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Filter to parse URLs and convert them to {@link GitilesView}s. */
public class ViewFilter extends AbstractHttpFilter {
  // TODO(dborowitz): Make this public in JGit (or implement getRegexGroup
  // upstream).
  private static final String REGEX_GROUPS_ATTRIBUTE =
      "org.eclipse.jgit.http.server.glue.MetaServlet.serveRegex";

  private static final String VIEW_ATTIRBUTE = ViewFilter.class.getName() + "/View";

  private static final String CMD_AUTO = "+";
  private static final String CMD_DIFF = "+diff";
  private static final String CMD_LOG = "+log";
  private static final String CMD_REFS = "+refs";
  private static final String CMD_SHOW = "+show";

  public static GitilesView getView(HttpServletRequest req) {
    return (GitilesView) req.getAttribute(VIEW_ATTIRBUTE);
  }

  static String getRegexGroup(HttpServletRequest req, int groupId) {
    WrappedRequest[] groups = (WrappedRequest[]) req.getAttribute(REGEX_GROUPS_ATTRIBUTE);
    return checkNotNull(groups)[groupId].getPathInfo();
  }

  static void setView(HttpServletRequest req, GitilesView view) {
    req.setAttribute(VIEW_ATTIRBUTE, view);
  }

  static String trimLeadingSlash(String str) {
    checkArgument(str.startsWith("/"), "expected string starting with a slash: %s", str);
    return str.substring(1);
  }

  private final GitilesUrls urls;
  private final GitilesAccess.Factory accessFactory;
  private final VisibilityCache visibilityCache;

  public ViewFilter(GitilesAccess.Factory accessFactory, GitilesUrls urls,
      VisibilityCache visibilityCache) {
    this.urls = checkNotNull(urls, "urls");
    this.accessFactory = checkNotNull(accessFactory, "accessFactory");
    this.visibilityCache = checkNotNull(visibilityCache, "visibilityCache");
  }

  @Override
  public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    GitilesView.Builder view = parse(req);
    if (view == null) {
      res.setStatus(SC_NOT_FOUND);
      return;
    }
    @SuppressWarnings("unchecked")
    Map<String, String[]> params = req.getParameterMap();
    view.setHostName(urls.getHostName(req))
        .setServletPath(req.getContextPath() + req.getServletPath())
        .putAllParams(params);
    setView(req, view.build());
    try {
      chain.doFilter(req, res);
    } finally {
      req.removeAttribute(VIEW_ATTIRBUTE);
    }
  }

  private GitilesView.Builder parse(HttpServletRequest req) throws IOException {
    String repoName = trimLeadingSlash(getRegexGroup(req, 1));
    String command = getRegexGroup(req, 2);
    String path = getRegexGroup(req, 3);

    // Non-path cases.
    if (repoName.isEmpty()) {
      return GitilesView.hostIndex();
    } else if (command.equals(CMD_REFS) && path.isEmpty()) {
      return GitilesView.refs().setRepositoryName(repoName);
    } else if (command.equals(CMD_LOG) && (path.isEmpty() || path.equals("/"))) {
      return GitilesView.log().setRepositoryName(repoName);
    } else if (command.isEmpty()) {
      return GitilesView.repositoryIndex().setRepositoryName(repoName);
    } else if (path.isEmpty()) {
      return null; // Command that requires a path, but no path.
    }

    path = trimLeadingSlash(path);
    RevisionParser revParser = new RevisionParser(
        ServletUtils.getRepository(req), accessFactory.forRequest(req), visibilityCache);
    RevisionParser.Result result = revParser.parse(path);
    if (result == null) {
      return null;
    }
    path = path.substring(result.getPathStart());

    command = getCommand(command, result, path);
    GitilesView.Builder view;
    if (CMD_LOG.equals(command)) {
      view = GitilesView.log().setTreePath(path);
    } else if (CMD_SHOW.equals(command)) {
      if (path.isEmpty()) {
        view = GitilesView.revision();
      } else {
        view = GitilesView.path().setTreePath(path);
      }
    } else if (CMD_DIFF.equals(command)) {
      view = GitilesView.diff().setTreePath(path);
    } else if (CMD_REFS.equals(command)) {
      view = GitilesView.repositoryIndex();
    } else {
      return null; // Bad command.
    }
    if (result.getOldRevision() != null) { // May be NULL.
      view.setOldRevision(result.getOldRevision());
    }
    view.setRepositoryName(repoName)
        .setRevision(result.getRevision());
    return view;
  }

  private String getCommand(String command, RevisionParser.Result result, String path) {
    // Note: if you change the mapping for +, make sure to change
    // GitilesView.toUrl() correspondingly.
    if (!CMD_AUTO.equals(command)) {
      return command;
    } else if (result.getOldRevision() != null) {
      return CMD_DIFF;
    } else {
      return CMD_SHOW;
    }
  }
}
