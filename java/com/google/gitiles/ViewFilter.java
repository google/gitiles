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
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_REPOSITORY;

import com.google.common.base.Strings;
import com.google.gitiles.GitilesRequestFailureException.FailureReason;
import java.io.IOException;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.http.server.glue.WrappedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Filter to parse URLs and convert them to {@link GitilesView}s. */
public class ViewFilter extends AbstractHttpFilter {
  private static final Logger log = LoggerFactory.getLogger(ViewFilter.class);
  // TODO(dborowitz): Make this public in JGit (or implement getRegexGroup
  // upstream).
  private static final String REGEX_GROUPS_ATTRIBUTE =
      "org.eclipse.jgit.http.server.glue.MetaServlet.serveRegex";

  private static final String VIEW_ATTRIBUTE = ViewFilter.class.getName() + "/View";

  private static final String CMD_ARCHIVE = "+archive";
  private static final String CMD_AUTO = "+";
  private static final String CMD_BLAME = "+blame";
  private static final String CMD_DESCRIBE = "+describe";
  private static final String CMD_DIFF = "+diff";
  private static final String CMD_LOG = "+log";
  private static final String CMD_REFS = "+refs";
  private static final String CMD_SHOW = "+show";
  private static final String CMD_DOC = "+doc";

  public static GitilesView getView(HttpServletRequest req) {
    return (GitilesView) req.getAttribute(VIEW_ATTRIBUTE);
  }

  static String getRegexGroup(HttpServletRequest req, int groupId) {
    WrappedRequest[] groups = (WrappedRequest[]) req.getAttribute(REGEX_GROUPS_ATTRIBUTE);
    return checkNotNull(groups)[groupId].getPathInfo();
  }

  static void setView(HttpServletRequest req, GitilesView view) {
    req.setAttribute(VIEW_ATTRIBUTE, view);
  }

  static void removeView(HttpServletRequest req) {
    req.removeAttribute(VIEW_ATTRIBUTE);
  }

  static String trimLeadingSlash(String str) {
    return checkLeadingSlash(str).substring(1);
  }

  private static String checkLeadingSlash(String str) {
    checkArgument(str.startsWith("/"), "expected string starting with a slash: %s", str);
    return str;
  }

  private static boolean isEmptyOrSlash(String path) {
    return path.isEmpty() || path.equals("/");
  }

  private static boolean hasRepository(HttpServletRequest req) {
    return req.getAttribute(ATTRIBUTE_REPOSITORY) != null;
  }

  private final GitilesUrls urls;
  private final GitilesAccess.Factory accessFactory;
  private final VisibilityCache visibilityCache;

  public ViewFilter(
      GitilesAccess.Factory accessFactory, GitilesUrls urls, VisibilityCache visibilityCache) {
    this.urls = checkNotNull(urls, "urls");
    this.accessFactory = checkNotNull(accessFactory, "accessFactory");
    this.visibilityCache = checkNotNull(visibilityCache, "visibilityCache");
  }

  @Override
  public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    GitilesView.Builder view = parse(req);
    if (view == null) {
      throw new GitilesRequestFailureException(FailureReason.CANNOT_PARSE_GITILES_VIEW);
    }

    @SuppressWarnings("unchecked")
    Map<String, String[]> params = req.getParameterMap();
    view.setHostName(urls.getHostName(req))
        .setServletPath(req.getContextPath() + req.getServletPath())
        .putAllParams(params);
    if (normalize(view, res)) {
      return;
    }

    setView(req, view.build());
    try {
      chain.doFilter(req, res);
    } finally {
      removeView(req);
    }
  }

  private boolean normalize(GitilesView.Builder view, HttpServletResponse res) throws IOException {
    if (view.getOldRevision() != Revision.NULL) {
      return false;
    }
    Revision r = view.getRevision();
    Revision nr = Revision.normalizeParentExpressions(r);
    if (r != nr) {
      res.sendRedirect(view.setRevision(nr).toUrl());
      return true;
    }
    return false;
  }

  private GitilesView.Builder parse(HttpServletRequest req) throws IOException {
    String repoName = trimLeadingSlash(getRegexGroup(req, 1));
    if (repoName.isEmpty()) {
      return GitilesView.hostIndex();
    }
    String command = getRegexGroup(req, 2);
    String path = getRegexGroup(req, 3);

    if (command.isEmpty()) {
      return parseNoCommand(req, repoName);
    }
    if (!hasRepository(req)) {
      return null; // no repository? return null to 404.
    }
    if (command.equals(CMD_ARCHIVE)) {
      return parseArchiveCommand(req, repoName, path);
    } else if (command.equals(CMD_AUTO)) {
      return parseAutoCommand(req, repoName, path);
    } else if (command.equals(CMD_BLAME)) {
      return parseBlameCommand(req, repoName, path);
    } else if (command.equals(CMD_DESCRIBE)) {
      return parseDescribeCommand(repoName, path);
    } else if (command.equals(CMD_DIFF)) {
      return parseDiffCommand(req, repoName, path);
    } else if (command.equals(CMD_LOG)) {
      return parseLogCommand(req, repoName, path);
    } else if (command.equals(CMD_REFS)) {
      return parseRefsCommand(repoName, path);
    } else if (command.equals(CMD_SHOW)) {
      return parseShowCommand(req, repoName, path);
    } else if (command.equals(CMD_DOC)) {
      return parseDocCommand(req, repoName, path);
    } else {
      return null;
    }
  }

  private GitilesView.Builder parseNoCommand(HttpServletRequest req, String repoName) {
    if (!hasRepository(req)) {
      return GitilesView.hostIndex().setRepositoryPrefix(repoName);
    }
    return GitilesView.repositoryIndex().setRepositoryName(repoName);
  }

  private GitilesView.Builder parseArchiveCommand(
      HttpServletRequest req, String repoName, String path) throws IOException {
    String ext = null;
    for (String e : ArchiveFormat.allExtensions()) {
      if (path.endsWith(e)) {
        path = path.substring(0, path.length() - e.length());
        ext = e;
        break;
      }
    }
    if (ext == null || path.endsWith("/")) {
      return null;
    }
    RevisionParser.Result result = parseRevision(req, path);
    if (result == null || result.getOldRevision() != null) {
      return null;
    }
    return GitilesView.archive()
        .setRepositoryName(repoName)
        .setRevision(result.getRevision())
        .setPathPart(Strings.emptyToNull(result.getPath()))
        .setExtension(ext);
  }

  private GitilesView.Builder parseAutoCommand(HttpServletRequest req, String repoName, String path)
      throws IOException {
    // Note: if you change the mapping for +, make sure to change
    // GitilesView.toUrl() correspondingly.
    if (path.isEmpty()) {
      return null;
    }
    RevisionParser.Result result = parseRevision(req, path);
    if (result == null) {
      return null;
    }
    if (result.getOldRevision() != null) {
      return parseDiffCommand(repoName, result);
    }
    GitilesView.Builder b = parseShowCommand(repoName, result);
    if (b != null && b.getPathPart() != null && b.getPathPart().endsWith(".md")) {
      return GitilesView.doc()
          .setRepositoryName(repoName)
          .setRevision(result.getRevision())
          .setPathPart(result.getPath());
    }
    return b;
  }

  private GitilesView.Builder parseBlameCommand(
      HttpServletRequest req, String repoName, String path) throws IOException {
    if (path.isEmpty()) {
      return null;
    }
    RevisionParser.Result result = parseRevision(req, path);
    if (result == null || result.getOldRevision() != null || result.getPath().isEmpty()) {
      return null;
    }
    return GitilesView.blame()
        .setRepositoryName(repoName)
        .setRevision(result.getRevision())
        .setPathPart(result.getPath());
  }

  private GitilesView.Builder parseDescribeCommand(String repoName, String path) {
    if (isEmptyOrSlash(path)) {
      return null;
    }
    return GitilesView.describe().setRepositoryName(repoName).setPathPart(path);
  }

  private GitilesView.Builder parseDiffCommand(HttpServletRequest req, String repoName, String path)
      throws IOException {
    return parseDiffCommand(repoName, parseRevision(req, path));
  }

  private GitilesView.Builder parseDiffCommand(String repoName, RevisionParser.Result result) {
    if (result == null) {
      return null;
    }
    return GitilesView.diff()
        .setRepositoryName(repoName)
        .setRevision(result.getRevision())
        .setOldRevision(result.getOldRevision())
        .setPathPart(result.getPath());
  }

  private GitilesView.Builder parseLogCommand(HttpServletRequest req, String repoName, String path)
      throws IOException {
    if (isEmptyOrSlash(path)) {
      return GitilesView.log().setRepositoryName(repoName);
    }
    RevisionParser.Result result = parseRevision(req, path);
    if (result == null) {
      return null;
    }
    return GitilesView.log()
        .setRepositoryName(repoName)
        .setRevision(result.getRevision())
        .setOldRevision(result.getOldRevision())
        .setPathPart(result.getPath());
  }

  private GitilesView.Builder parseRefsCommand(String repoName, String path) {
    return GitilesView.refs().setRepositoryName(repoName).setPathPart(path);
  }

  private GitilesView.Builder parseShowCommand(HttpServletRequest req, String repoName, String path)
      throws IOException {
    return parseShowCommand(repoName, parseRevision(req, path));
  }

  private GitilesView.Builder parseShowCommand(String repoName, RevisionParser.Result result) {
    if (result == null || result.getOldRevision() != null) {
      return null;
    }
    if (result.getPath().isEmpty()) {
      return GitilesView.revision().setRepositoryName(repoName).setRevision(result.getRevision());
    }
    return GitilesView.path()
        .setRepositoryName(repoName)
        .setRevision(result.getRevision())
        .setPathPart(result.getPath());
  }

  private GitilesView.Builder parseDocCommand(HttpServletRequest req, String repoName, String path)
      throws IOException {
    return parseDocCommand(repoName, parseRevision(req, path));
  }

  private GitilesView.Builder parseDocCommand(String repoName, RevisionParser.Result result) {
    if (result == null || result.getOldRevision() != null) {
      return null;
    }
    GitilesView.Builder b =
        GitilesView.doc().setRepositoryName(repoName).setRevision(result.getRevision());
    if (!result.getPath().isEmpty()) {
      b.setPathPart(result.getPath());
    }
    return b;
  }

  private RevisionParser.Result parseRevision(HttpServletRequest req, String path)
      throws IOException {
    RevisionParser revParser =
        new RevisionParser(
            ServletUtils.getRepository(req), accessFactory.forRequest(req), visibilityCache);
    return revParser.parse(checkLeadingSlash(path));
  }
}
