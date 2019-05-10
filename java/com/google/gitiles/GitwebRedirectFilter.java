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
import static com.google.common.net.HttpHeaders.LOCATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_PERMANENTLY;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gitiles.GitilesView.InvalidViewException;
import com.google.gitiles.GitilesRequestFailureException.FailureReason;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.ObjectId;

/** Filter to redirect Gitweb-style URLs to Gitiles-style URLs. */
public class GitwebRedirectFilter extends AbstractHttpFilter {
  public static class TooManyUriParametersException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
  }

  private static final String ARG_SEP = "&|;|%3[Bb]";
  private static final Pattern IS_GITWEB_PATTERN = Pattern.compile("(^|" + ARG_SEP + ")[pa]=");
  private static final Splitter ARG_SPLIT = Splitter.on(Pattern.compile(ARG_SEP));
  private static final Splitter VAR_SPLIT = Splitter.on(Pattern.compile("=|%3[Dd]")).limit(2);
  private static final int MAX_ARGS = 512;

  private final boolean trimDotGit;

  public GitwebRedirectFilter() {
    this(false);
  }

  public GitwebRedirectFilter(boolean trimDotGit) {
    this.trimDotGit = trimDotGit;
  }

  @Override
  public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    GitilesView gitwebView = ViewFilter.getView(req);
    if (!isGitwebStyleQuery(req)) {
      chain.doFilter(req, res);
      return;
    }

    ListMultimap<String, String> params = parse(req.getQueryString());
    String action = getFirst(params, "a");
    String project = getFirst(params, "p");
    String path = Strings.nullToEmpty(getFirst(params, "f"));

    // According to gitweb's perl source code, the primary parameters are these
    // short abbreviated names. When pointing to blob or subtree hash,hashParent
    // are the blob or subtree SHA-1s and hashBase,hashParentBase are commits.
    // When pointing to commits or tags, hash is the commit/tag. Its messy.
    Revision hash = toRevision(getFirst(params, "h"));
    Revision hashBase = toRevision(getFirst(params, "hb"));
    Revision hashParent = toRevision(getFirst(params, "hp"));
    Revision hashParentBase = toRevision(getFirst(params, "hpb"));

    GitilesView.Builder view;
    if ("project_index".equals(action)) {
      view = GitilesView.hostIndex();
      project = null;
    } else if ("summary".equals(action) || "tags".equals(action)) {
      view = GitilesView.repositoryIndex();
    } else if (("commit".equals(action) || "tag".equals(action)) && hash != null) {
      view = GitilesView.revision().setRevision(hash);
    } else if ("log".equals(action) || "shortlog".equals(action)) {
      view = GitilesView.log().setRevision(firstNonNull(hash, Revision.HEAD));
    } else if ("tree".equals(action)) {
      view =
          GitilesView.path().setRevision(firstNonNull(hashBase, Revision.HEAD)).setPathPart(path);
    } else if (("blob".equals(action) || "blob_plain".equals(action))
        && hashBase != null
        && !path.isEmpty()) {
      view = GitilesView.path().setRevision(hashBase).setPathPart(path);
    } else if ("commitdiff".equals(action) && hash != null) {
      view =
          GitilesView.diff()
              .setOldRevision(firstNonNull(hashParent, Revision.NULL))
              .setRevision(hash)
              .setPathPart("");
    } else if ("blobdiff".equals(action)
        && !path.isEmpty()
        && hashParentBase != null
        && hashBase != null) {
      view =
          GitilesView.diff().setOldRevision(hashParentBase).setRevision(hashBase).setPathPart(path);
    } else if ("history".equals(action) && !path.isEmpty()) {
      view = GitilesView.log().setRevision(firstNonNull(hashBase, Revision.HEAD)).setPathPart(path);
    } else {
      // Gitiles does not provide an RSS feed (a=rss,atom,opml)
      // Any other URL is out of date and not valid anymore.
      throw new GitilesRequestFailureException(FailureReason.UNSUPPORTED_GITWEB_URL);
    }

    if (!Strings.isNullOrEmpty(project)) {
      view.setRepositoryName(cleanProjectName(project));
    }

    String url;
    try {
      url =
          view.setHostName(gitwebView.getHostName())
              .setServletPath(gitwebView.getServletPath())
              .toUrl();
    } catch (InvalidViewException e) {
      throw new GitilesRequestFailureException(FailureReason.UNSUPPORTED_GITWEB_URL);
    }
    res.setStatus(SC_MOVED_PERMANENTLY);
    res.setHeader(LOCATION, url);
  }

  private static boolean isGitwebStyleQuery(HttpServletRequest req) {
    String qs = req.getQueryString();
    return qs != null && IS_GITWEB_PATTERN.matcher(qs).find();
  }

  private static String getFirst(ListMultimap<String, String> params, String name) {
    return Iterables.getFirst(params.get(checkNotNull(name)), null);
  }

  private static Revision toRevision(String rev) {
    if (Strings.isNullOrEmpty(rev)) {
      return null;
    } else if ("HEAD".equals(rev) || rev.startsWith("refs/")) {
      return Revision.named(rev);
    } else if (ObjectId.isId(rev)) {
      return Revision.unpeeled(rev, ObjectId.fromString(rev));
    } else {
      return Revision.named(rev);
    }
  }

  private String cleanProjectName(String p) {
    if (p.startsWith("/")) {
      p = p.substring(1);
    }
    if (p.endsWith("/")) {
      p = p.substring(0, p.length() - 1);
    }
    if (trimDotGit && p.endsWith(".git")) {
      p = p.substring(0, p.length() - ".git".length());
    }
    if (p.endsWith("/")) {
      p = p.substring(0, p.length() - 1);
    }
    return p;
  }

  private static ListMultimap<String, String> parse(String query) {
    // Parse a gitweb style query string which uses ";" rather than "&" between
    // key=value pairs. Some user agents encode ";" as "%3B" and/or "=" as
    // "%3D", making a real mess of the query string. Parsing here is
    // approximate because ; shouldn't be the pair separator and %3B might have
    // been a ; within a value.
    // This is why people shouldn't use gitweb.
    ListMultimap<String, String> map = LinkedListMultimap.create();
    for (String piece : ARG_SPLIT.split(query)) {
      if (map.size() > MAX_ARGS) {
        throw new TooManyUriParametersException();
      }

      List<String> pair = VAR_SPLIT.splitToList(piece);
      if (pair.size() == 2) {
        map.put(decode(pair.get(0)), decode(pair.get(1)));
      } else { // no equals sign
        map.put(piece, "");
      }
    }
    return map;
  }

  private static String decode(String str) {
    try {
      return URLDecoder.decode(str, UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      return str;
    }
  }
}
