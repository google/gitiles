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
import static com.google.common.base.Preconditions.checkState;
import static com.google.gitiles.GitilesUrls.NAME_ESCAPER;
import static com.google.gitiles.RevisionParser.PATH_SPLITTER;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;

import org.eclipse.jgit.revwalk.RevObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Information about a view in Gitiles.
 * <p>
 * Views are uniquely identified by a type, and dispatched to servlet types by
 * {@link GitilesServlet}. This class contains the list of all types, as
 * well as some methods containing basic information parsed from the URL.
 * Construction happens in {@link ViewFilter}.
 */
public class GitilesView {
  /** All the possible view types supported in the application. */
  public static enum Type {
    HOST_INDEX,
    REPOSITORY_INDEX,
    REVISION,
    PATH,
    DIFF,
    LOG;
  }

  /** Builder for views. */
  public static class Builder {
    private final Type type;
    private final ListMultimap<String, String> params = LinkedListMultimap.create();

    private String hostName;
    private String servletPath;
    private String repositoryName;
    private Revision revision = Revision.NULL;
    private Revision oldRevision = Revision.NULL;
    private String path;
    private String anchor;

    private Builder(Type type) {
      this.type = type;
    }

    public Builder copyFrom(GitilesView other) {
      hostName = other.hostName;
      servletPath = other.servletPath;
      switch (type) {
        case LOG:
        case DIFF:
          oldRevision = other.oldRevision;
          // Fallthrough.
        case PATH:
          path = other.path;
          // Fallthrough.
        case REVISION:
          revision = other.revision;
          // Fallthrough.
        case REPOSITORY_INDEX:
          repositoryName = other.repositoryName;
          // Fallthrough.
        default:
          break;
      }
      // Don't copy params.
      return this;
    }

    public Builder copyFrom(HttpServletRequest req) {
      return copyFrom(ViewFilter.getView(req));
    }

    public Builder setHostName(String hostName) {
      this.hostName = checkNotNull(hostName);
      return this;
    }

    public String getHostName() {
      return hostName;
    }

    public Builder setServletPath(String servletPath) {
      this.servletPath = checkNotNull(servletPath);
      return this;
    }

    public String getServletPath() {
      return servletPath;
    }

    public Builder setRepositoryName(String repositoryName) {
      switch (type) {
        case HOST_INDEX:
          throw new IllegalStateException(String.format(
              "cannot set repository name on %s view", type));
        default:
          this.repositoryName = checkNotNull(repositoryName);
          return this;
      }
    }

    public String getRepositoryName() {
      return repositoryName;
    }

    public Builder setRevision(Revision revision) {
      switch (type) {
        case HOST_INDEX:
        case REPOSITORY_INDEX:
          throw new IllegalStateException(String.format("cannot set revision on %s view", type));
        default:
          this.revision = checkNotNull(revision);
          return this;
      }
    }

    public Builder setRevision(String name) {
      return setRevision(Revision.named(name));
    }

    public Builder setRevision(RevObject obj) {
      return setRevision(Revision.peeled(obj.name(), obj));
    }

    public Builder setRevision(String name, RevObject obj) {
      return setRevision(Revision.peeled(name, obj));
    }

    public Revision getRevision() {
      return revision;
    }

    public Builder setOldRevision(Revision revision) {
      switch (type) {
        case DIFF:
        case LOG:
          this.oldRevision = checkNotNull(revision);
          return this;
        default:
          throw new IllegalStateException(
              String.format("cannot set old revision on %s view", type));
      }
    }

    public Builder setOldRevision(RevObject obj) {
      return setOldRevision(Revision.peeled(obj.name(), obj));
    }

    public Builder setOldRevision(String name, RevObject obj) {
      return setOldRevision(Revision.peeled(name, obj));
    }

    public Revision getOldRevision() {
      return revision;
    }

    public Builder setTreePath(String path) {
      switch (type) {
        case PATH:
        case DIFF:
          this.path = maybeTrimLeadingAndTrailingSlash(checkNotNull(path));
          return this;
        case LOG:
          this.path = path != null ? maybeTrimLeadingAndTrailingSlash(path) : null;
          return this;
        default:
          throw new IllegalStateException(String.format("cannot set path on %s view", type));
      }
    }

    public String getTreePath() {
      return path;
    }

    public Builder putParam(String key, String value) {
      params.put(key, value);
      return this;
    }

    public Builder replaceParam(String key, String value) {
      params.replaceValues(key, ImmutableList.of(value));
      return this;
    }

    public Builder putAllParams(Map<String, String[]> params) {
      for (Map.Entry<String, String[]> e : params.entrySet()) {
        for (String v : e.getValue()) {
          this.params.put(e.getKey(), v);
        }
      }
      return this;
    }

    public ListMultimap<String, String> getParams() {
      return params;
    }

    public Builder setAnchor(String anchor) {
      this.anchor = anchor;
      return this;
    }

    public String getAnchor() {
      return anchor;
    }

    public GitilesView build() {
      switch (type) {
        case HOST_INDEX:
          checkHostIndex();
          break;
        case REPOSITORY_INDEX:
          checkRepositoryIndex();
          break;
        case REVISION:
          checkRevision();
          break;
        case PATH:
          checkPath();
          break;
        case DIFF:
          checkDiff();
          break;
        case LOG:
          checkLog();
          break;
      }
      return new GitilesView(type, hostName, servletPath, repositoryName, revision,
          oldRevision, path, params, anchor);
    }

    public String toUrl() {
      return build().toUrl();
    }

    private void checkHostIndex() {
      checkState(hostName != null, "missing hostName on %s view", type);
      checkState(servletPath != null, "missing hostName on %s view", type);
    }

    private void checkRepositoryIndex() {
      checkState(repositoryName != null, "missing repository name on %s view", type);
      checkHostIndex();
    }

    private void checkRevision() {
      checkState(revision != Revision.NULL, "missing revision on %s view", type);
      checkRepositoryIndex();
    }

    private void checkDiff() {
      checkPath();
    }

    private void checkLog() {
      checkRevision();
    }

    private void checkPath() {
      checkState(path != null, "missing path on %s view", type);
      checkRevision();
    }
  }

  public static Builder hostIndex() {
    return new Builder(Type.HOST_INDEX);
  }

  public static Builder repositoryIndex() {
    return new Builder(Type.REPOSITORY_INDEX);
  }

  public static Builder revision() {
    return new Builder(Type.REVISION);
  }

  public static Builder path() {
    return new Builder(Type.PATH);
  }

  public static Builder diff() {
    return new Builder(Type.DIFF);
  }

  public static Builder log() {
    return new Builder(Type.LOG);
  }

  private static String maybeTrimLeadingAndTrailingSlash(String str) {
    if (str.startsWith("/")) {
      str = str.substring(1);
    }
    return !str.isEmpty() && str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
  }

  private final Type type;
  private final String hostName;
  private final String servletPath;
  private final String repositoryName;
  private final Revision revision;
  private final Revision oldRevision;
  private final String path;
  private final ListMultimap<String, String> params;
  private final String anchor;

  private GitilesView(Type type,
      String hostName,
      String servletPath,
      String repositoryName,
      Revision revision,
      Revision oldRevision,
      String path,
      ListMultimap<String, String> params,
      String anchor) {
    this.type = type;
    this.hostName = hostName;
    this.servletPath = servletPath;
    this.repositoryName = repositoryName;
    this.revision = Objects.firstNonNull(revision, Revision.NULL);
    this.oldRevision = Objects.firstNonNull(oldRevision, Revision.NULL);
    this.path = path;
    this.params = Multimaps.unmodifiableListMultimap(params);
    this.anchor = anchor;
  }

  public String getHostName() {
    return hostName;
  }

  public String getServletPath() {
    return servletPath;
  }

  public String getRepositoryName() {
    return repositoryName;
  }

  public Revision getRevision() {
    return revision;
  }

  public Revision getOldRevision() {
    return oldRevision;
  }

  public String getRevisionRange() {
    if (oldRevision == Revision.NULL) {
      switch (type) {
        case LOG:
        case DIFF:
          // For types that require two revisions, NULL indicates the empty
          // tree/commit.
          return revision.getName() + "^!";
        default:
          // For everything else NULL indicates it is not a range, just a single
          // revision.
          return null;
      }
    } else if (type == Type.DIFF && isFirstParent(revision, oldRevision)) {
      return revision.getName() + "^!";
    } else {
      return oldRevision.getName() + ".." + revision.getName();
    }
  }

  public String getTreePath() {
    return path;
  }

  public ListMultimap<String, String> getParameters() {
    return params;
  }

  public String getAnchor() {
    return anchor;
  }

  public Type getType() {
    return type;
  }

  /** @return an escaped, relative URL representing this view. */
  public String toUrl() {
    StringBuilder url = new StringBuilder(servletPath).append('/');
    ListMultimap<String, String> params = this.params;
    switch (type) {
      case HOST_INDEX:
        params = LinkedListMultimap.create();
        if (!this.params.containsKey("format")) {
          params.put("format", FormatType.HTML.toString());
        }
        params.putAll(this.params);
        break;
      case REPOSITORY_INDEX:
        url.append(repositoryName).append('/');
        break;
      case REVISION:
        url.append(repositoryName).append("/+/").append(revision.getName());
        break;
      case PATH:
        url.append(repositoryName).append("/+/").append(revision.getName()).append('/')
            .append(path);
        break;
      case DIFF:
        url.append(repositoryName).append("/+/");
        if (isFirstParent(revision, oldRevision)) {
          url.append(revision.getName()).append("^!");
        } else {
          url.append(oldRevision.getName()).append("..").append(revision.getName());
        }
        url.append('/').append(path);
        break;
      case LOG:
        url.append(repositoryName).append("/+log/");
        if (oldRevision != Revision.NULL) {
          url.append(oldRevision.getName()).append("..");
        }
        url.append(revision.getName());
        if (path != null) {
          url.append('/').append(path);
        }
        break;
      default:
        throw new IllegalStateException("Unknown view type: " + type);
    }
    String baseUrl = NAME_ESCAPER.apply(url.toString());
    url = new StringBuilder();
    if (!params.isEmpty()) {
      url.append('?').append(paramsToString(params));
    }
    if (!Strings.isNullOrEmpty(anchor)) {
      url.append('#').append(NAME_ESCAPER.apply(anchor));
    }
    return baseUrl + url.toString();
  }

  /**
   * @return a list of maps with "text" and "url" keys for all file paths
   *     leading up to the path represented by this view. All URLs allow
   *     auto-diving into one-entry subtrees; see also
   *     {@link #getBreadcrumbs(List<Boolean>)}.
   */
  public List<Map<String, String>> getBreadcrumbs() {
    return getBreadcrumbs(null);
  }

  /**
   * @param hasSingleTree list of booleans, one per path entry in this view's
   *     path excluding the leaf. True entries indicate the tree at that path
   *     only has a single entry that is another tree.
   * @return a list of maps with "text" and "url" keys for all file paths
   *     leading up to the path represented by this view. URLs whose
   *     corresponding entry in {@code hasSingleTree} is true will disable
   *     auto-diving into one-entry subtrees.
   */
  public List<Map<String, String>> getBreadcrumbs(List<Boolean> hasSingleTree) {
    checkArgument(hasSingleTree == null || type == Type.PATH,
        "hasSingleTree must be null for %s view", type);
    String path = this.path;
    ImmutableList.Builder<Map<String, String>> breadcrumbs = ImmutableList.builder();
    breadcrumbs.add(breadcrumb(hostName, hostIndex().copyFrom(this)));
    if (repositoryName != null) {
      breadcrumbs.add(breadcrumb(repositoryName, repositoryIndex().copyFrom(this)));
    }
    if (type == Type.DIFF) {
      // TODO(dborowitz): Tweak the breadcrumbs template to allow us to render
      // separate links in "old..new".
      breadcrumbs.add(breadcrumb(getRevisionRange(), diff().copyFrom(this).setTreePath("")));
    } else if (type == Type.LOG) {
      // TODO(dborowitz): Add something in the navigation area (probably not
      // a breadcrumb) to allow switching between /+log/ and /+/.
      if (oldRevision == Revision.NULL) {
        breadcrumbs.add(breadcrumb(revision.getName(), log().copyFrom(this).setTreePath(null)));
      } else {
        breadcrumbs.add(breadcrumb(getRevisionRange(), log().copyFrom(this).setTreePath(null)));
      }
      path = Strings.emptyToNull(path);
    } else if (revision != Revision.NULL) {
      breadcrumbs.add(breadcrumb(revision.getName(), revision().copyFrom(this)));
    }
    if (path != null) {
      if (type != Type.LOG) { // The "." breadcrumb would be no different for LOG.
        breadcrumbs.add(breadcrumb(".", copyWithPath().setTreePath("")));
      }
      StringBuilder cur = new StringBuilder();
      List<String> parts = ImmutableList.copyOf(PATH_SPLITTER.omitEmptyStrings().split(path));
      checkArgument(hasSingleTree == null || hasSingleTree.size() == parts.size() - 1,
          "hasSingleTree has wrong number of entries");
      for (int i = 0; i < parts.size(); i++) {
        String part = parts.get(i);
        cur.append(part).append('/');
        String curPath = cur.toString();
        Builder builder = copyWithPath().setTreePath(curPath);
        if (hasSingleTree != null && i < parts.size() - 1 && hasSingleTree.get(i)) {
          builder.replaceParam(PathServlet.AUTODIVE_PARAM, PathServlet.NO_AUTODIVE_VALUE);
        }
        breadcrumbs.add(breadcrumb(part, builder));
      }
    }
    return breadcrumbs.build();
  }

  private static Map<String, String> breadcrumb(String text, Builder url) {
    return ImmutableMap.of("text", text, "url", url.toUrl());
  }

  private Builder copyWithPath() {
    Builder copy;
    switch (type) {
      case DIFF:
        copy = diff();
        break;
      case LOG:
        copy = log();
        break;
      default:
        copy = path();
        break;
    }
    return copy.copyFrom(this);
  }

  private static boolean isFirstParent(Revision rev1, Revision rev2) {
    return rev2 == Revision.NULL
        || rev2.getName().equals(rev1.getName() + "^")
        || rev2.getName().equals(rev1.getName() + "~1");
  }

  private static String paramsToString(ListMultimap<String, String> params) {
    try {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> e : params.entries()) {
      if (!first) {
        sb.append('&');
      } else {
        first = false;
      }
      sb.append(URLEncoder.encode(e.getKey(), Charsets.UTF_8.name()));
      if (!"".equals(e.getValue())) {
        sb.append('=')
            .append(URLEncoder.encode(e.getValue(), Charsets.UTF_8.name()));
      }
    }
    return sb.toString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
