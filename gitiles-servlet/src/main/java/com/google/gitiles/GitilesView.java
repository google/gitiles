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
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gitiles.GitilesUrls.NAME_ESCAPER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
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
  private static final String DEFAULT_ARCHIVE_EXTENSION = ".tar.gz";

  /** All the possible view types supported in the application. */
  public enum Type {
    HOST_INDEX,
    REPOSITORY_INDEX,
    REFS,
    REVISION,
    PATH,
    SHOW,
    DIFF,
    LOG,
    DESCRIBE,
    ARCHIVE,
    BLAME,
    DOC,
    ROOTED_DOC;
  }

  /** Exception thrown when building a view that is invalid. */
  public static class InvalidViewException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public InvalidViewException(String msg) {
      super(msg);
    }
  }

  /** Builder for views. */
  public static class Builder {
    private Type type;
    private final ListMultimap<String, String> params = LinkedListMultimap.create();

    private String hostName;
    private String servletPath;
    private String repositoryPrefix;
    private String repositoryName;
    private Revision revision = Revision.NULL;
    private Revision oldRevision = Revision.NULL;
    private String path;
    private String extension;
    private String anchor;

    private Builder(Type type) {
      this.type = type;
    }

    public Builder copyFrom(GitilesView other) {
      if (type == Type.DOC && other.type == Type.ROOTED_DOC) {
        type = Type.ROOTED_DOC;
      }

      hostName = other.hostName;
      servletPath = other.servletPath;
      switch (type) {
        case HOST_INDEX:
          repositoryPrefix = other.repositoryPrefix;
          break;
        case LOG:
        case DIFF:
          oldRevision = other.oldRevision;
          //$FALL-THROUGH$
        case PATH:
        case DOC:
        case ROOTED_DOC:
        case ARCHIVE:
        case BLAME:
        case SHOW:
          path = other.path;
          //$FALL-THROUGH$
        case REVISION:
          revision = other.revision;
          //$FALL-THROUGH$
        case DESCRIBE:
        case REFS:
        case REPOSITORY_INDEX:
          repositoryName = other.repositoryName;
          //$FALL-THROUGH$
        default:
          break;
      }
      if (other.type == type) {
        // Only copy params for matching type.
        params.putAll(other.params);
        if (type == Type.ARCHIVE) {
          extension = other.extension;
        }
      }
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

    public Builder setRepositoryPrefix(String prefix) {
      if (type == Type.HOST_INDEX) {
        this.repositoryPrefix = prefix != null
            ? Strings.emptyToNull(maybeTrimLeadingAndTrailingSlash(prefix))
            : null;
        return this;
      }
      throw new IllegalStateException(
          String.format("cannot set repository prefix on %s view", type));
    }

    public Builder setRepositoryName(String repositoryName) {
      if (type == Type.HOST_INDEX) {
        throw new IllegalStateException(
            String.format("cannot set repository name on %s view", type));
      }
      this.repositoryName = checkNotNull(repositoryName);
      return this;
    }

    public String getRepositoryName() {
      return repositoryName;
    }

    public Builder setRevision(Revision revision) {
      switch (type) {
        case HOST_INDEX:
        case REPOSITORY_INDEX:
        case REFS:
        case DESCRIBE:
          throw new IllegalStateException(String.format("cannot set revision on %s view", type));
        case ARCHIVE:
        case BLAME:
        case DIFF:
        case DOC:
        case LOG:
        case PATH:
        case REVISION:
        case ROOTED_DOC:
        case SHOW:
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
      if (type != Type.DIFF && type != Type.LOG) {
        revision = firstNonNull(revision, Revision.NULL);
        checkState(revision == Revision.NULL, "cannot set old revision on %s view", type);
      }
      this.oldRevision = revision;
      return this;
    }

    public Builder setOldRevision(RevObject obj) {
      return setOldRevision(Revision.peeled(obj.name(), obj));
    }

    public Builder setOldRevision(String name, RevObject obj) {
      return setOldRevision(Revision.peeled(name, obj));
    }

    public Revision getOldRevision() {
      return oldRevision;
    }

    public Builder setPathPart(String path) {
      switch (type) {
        case PATH:
        case DIFF:
        case SHOW:
          checkState(path != null, "cannot set null path on %s view", type);
          break;
        case BLAME:
        case ARCHIVE:
        case DESCRIBE:
        case REFS:
        case LOG:
        case DOC:
        case ROOTED_DOC:
          break;
        case HOST_INDEX:
        case REPOSITORY_INDEX:
        case REVISION:
        default:
          checkState(path == null, "cannot set path on %s view", type);
          break;
      }
      this.path = path != null ? maybeTrimLeadingAndTrailingSlash(path) : null;
      return this;
    }

    public String getPathPart() {
      return path;
    }

    public Builder setExtension(String extension) {
      if (type != Type.ARCHIVE) {
        checkState(extension == null, "cannot set extension on %s view", type);
      }
      this.extension = extension;
      return this;
    }

    public String getExtension() {
      return extension;
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
        this.params.putAll(e.getKey(), Arrays.asList(e.getValue()));
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
        case REFS:
          checkRefs();
          break;
        case DESCRIBE:
          checkDescribe();
          break;
        case REVISION:
          checkRevision();
          break;
        case PATH:
        case SHOW:
        case DOC:
          checkPath();
          break;
        case DIFF:
          checkDiff();
          break;
        case LOG:
          checkLog();
          break;
        case ARCHIVE:
          checkArchive();
          break;
        case BLAME:
          checkBlame();
          break;
        case ROOTED_DOC:
          checkRootedDoc();
          break;
      }
      return new GitilesView(
          type,
          hostName,
          servletPath,
          repositoryPrefix,
          repositoryName,
          revision,
          oldRevision,
          path,
          extension,
          params,
          anchor);
    }

    public String toUrl() {
      return build().toUrl();
    }

    private void checkView(boolean expr, String msg, Object... args) {
      if (!expr) {
        throw new InvalidViewException(String.format(msg, args));
      }
    }

    private void checkHostIndex() {
      checkView(hostName != null, "missing hostName on %s view", type);
      checkView(servletPath != null, "missing servletPath on %s view", type);
    }

    private void checkRepositoryIndex() {
      checkView(repositoryName != null, "missing repository name on %s view", type);
      checkHostIndex();
    }

    private void checkRefs() {
      checkRepositoryIndex();
    }

    private void checkDescribe() {
      checkRepositoryIndex();
    }

    private void checkRevision() {
      checkView(revision != Revision.NULL, "missing revision on %s view", type);
      checkRepositoryIndex();
    }

    private void checkDiff() {
      checkPath();
    }

    private void checkLog() {
      checkRepositoryIndex();
    }

    private void checkPath() {
      checkView(path != null, "missing path on %s view", type);
      checkRevision();
    }

    private void checkArchive() {
      checkRevision();
    }

    private void checkBlame() {
      checkPath();
    }

    private void checkRootedDoc() {
      checkView(hostName != null, "missing hostName on %s view", type);
      checkView(servletPath != null, "missing hostName on %s view", type);
      checkView(revision != Revision.NULL, "missing revision on %s view", type);
      checkView(path != null, "missing path on %s view", type);
    }
  }

  public static Builder hostIndex() {
    return new Builder(Type.HOST_INDEX);
  }

  public static Builder repositoryIndex() {
    return new Builder(Type.REPOSITORY_INDEX);
  }

  public static Builder refs() {
    return new Builder(Type.REFS);
  }

  public static Builder describe() {
    return new Builder(Type.DESCRIBE);
  }

  public static Builder revision() {
    return new Builder(Type.REVISION);
  }

  public static Builder path() {
    return new Builder(Type.PATH);
  }

  public static Builder show() {
    return new Builder(Type.SHOW);
  }

  public static Builder diff() {
    return new Builder(Type.DIFF);
  }

  public static Builder log() {
    return new Builder(Type.LOG);
  }

  public static Builder archive() {
    return new Builder(Type.ARCHIVE);
  }

  public static Builder blame() {
    return new Builder(Type.BLAME);
  }

  public static Builder doc() {
    return new Builder(Type.DOC);
  }

  public static Builder rootedDoc() {
    return new Builder(Type.ROOTED_DOC);
  }

  static String maybeTrimLeadingAndTrailingSlash(String str) {
    if (str.startsWith("/")) {
      str = str.substring(1);
    }
    return !str.isEmpty() && str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
  }

  private final Type type;
  private final String hostName;
  private final String servletPath;
  private final String repositoryPrefix;
  private final String repositoryName;
  private final Revision revision;
  private final Revision oldRevision;
  private final String path;
  private final String extension;
  private final ListMultimap<String, String> params;
  private final String anchor;

  private GitilesView(
      Type type,
      String hostName,
      String servletPath,
      String repositoryPrefix,
      String repositoryName,
      Revision revision,
      Revision oldRevision,
      String path,
      String extension,
      ListMultimap<String, String> params,
      String anchor) {
    this.type = type;
    this.hostName = hostName;
    this.servletPath = servletPath;
    this.repositoryPrefix = repositoryPrefix;
    this.repositoryName = repositoryName;
    this.revision = firstNonNull(revision, Revision.NULL);
    this.oldRevision = firstNonNull(oldRevision, Revision.NULL);
    this.path = path;
    this.extension = extension;
    this.params = Multimaps.unmodifiableListMultimap(params);
    this.anchor = anchor;
  }

  public Builder copyFrom(GitilesView other) {
    return new Builder(other.type).copyFrom(this);
  }

  public Builder toBuilder() {
    return copyFrom(this);
  }

  public String getHostName() {
    return hostName;
  }

  public String getServletPath() {
    return servletPath;
  }

  public String getRepositoryPrefix() {
    return repositoryPrefix;
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
      if (type == Type.LOG || type == Type.DIFF) {
        // For types that require two revisions, NULL indicates the empty
        // tree/commit.
        return revision.getName() + "^!";
      }
      // For everything else NULL indicates it is not a range, just a single
      // revision.
      return null;
    } else if (type == Type.DIFF && isFirstParent(revision, oldRevision)) {
      return revision.getName() + "^!";
    } else {
      return oldRevision.getName() + ".." + revision.getName();
    }
  }

  public String getPathPart() {
    return path;
  }

  public String getExtension() {
    return extension;
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

  @Override
  public String toString() {
    ToStringHelper b =
        toStringHelper(type.toString())
            .omitNullValues()
            .add("host", hostName)
            .add("servlet", servletPath)
            .add("prefix", repositoryPrefix)
            .add("repo", repositoryName)
            .add("rev", revision)
            .add("old", oldRevision)
            .add("path", path)
            .add("extension", extension);
    if (!params.isEmpty()) {
      b.add("params", params);
    }
    b.add("anchor", anchor);
    return b.toString();
  }

  /** @return an escaped, relative URL representing this view. */
  public String toUrl() {
    StringBuilder url = new StringBuilder(servletPath).append('/');
    ListMultimap<String, String> params = this.params;
    switch (type) {
      case HOST_INDEX:
        if (repositoryPrefix != null) {
          url.append(repositoryPrefix).append('/');
        }
        params = LinkedListMultimap.create();
        if (repositoryPrefix == null && !this.params.containsKey("format")) {
          params.put("format", FormatType.HTML.toString());
        }
        params.putAll(this.params);
        break;
      case REPOSITORY_INDEX:
        url.append(repositoryName).append('/');
        break;
      case REFS:
        url.append(repositoryName).append("/+refs");
        break;
      case DESCRIBE:
        url.append(repositoryName).append("/+describe");
        break;
      case REVISION:
        url.append(repositoryName).append("/+/").append(revision.getName());
        break;
      case ARCHIVE:
        url.append(repositoryName).append("/+archive/").append(revision.getName());
        if (path != null) {
          url.append('/').append(path);
        }
        url.append(firstNonNull(extension, DEFAULT_ARCHIVE_EXTENSION));
        break;
      case PATH:
        url.append(repositoryName)
            .append("/+/")
            .append(revision.getName())
            .append('/')
            .append(path);
        break;
      case SHOW:
        url.append(repositoryName)
            .append("/+show/")
            .append(revision.getName())
            .append('/')
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
        url.append(repositoryName).append("/+log");
        if (revision != Revision.NULL) {
          url.append('/');
          if (oldRevision != Revision.NULL) {
            url.append(oldRevision.getName()).append("..");
          }
          url.append(revision.getName());
          if (path != null) {
            url.append('/').append(path);
          }
        }
        break;
      case BLAME:
        url.append(repositoryName)
            .append("/+blame/")
            .append(revision.getName())
            .append('/')
            .append(path);
        break;
      case DOC:
        url.append(repositoryName);
        if (path != null && path.endsWith(".md")) {
          url.append("/+/");
        } else {
          url.append("/+doc/");
        }
        url.append(revision.getName());
        if (path != null) {
          url.append('/').append(path);
        }
        break;
      case ROOTED_DOC:
        if (path != null) {
          url.append(path);
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
    return baseUrl + url;
  }

  /**
   * @return a list of maps with "text" and "url" keys for all file paths
   *     leading up to the path represented by this view. All URLs allow
   *     auto-diving into one-entry subtrees; see also
   *     {@link #getBreadcrumbs(List)}.
   */
  public List<Map<String, String>> getBreadcrumbs() {
    return getBreadcrumbs(null);
  }

  private static final EnumSet<Type> NON_HTML_TYPES = EnumSet.of(Type.DESCRIBE, Type.ARCHIVE);

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
    checkArgument(!NON_HTML_TYPES.contains(type), "breadcrumbs for %s view not supported", type);
    checkArgument(
        type != Type.REFS || Strings.isNullOrEmpty(path),
        "breadcrumbs for REFS view with path not supported");
    checkArgument(
        hasSingleTree == null || type == Type.PATH, "hasSingleTree must be null for %s view", type);
    String path = this.path;
    ImmutableList.Builder<Map<String, String>> breadcrumbs = ImmutableList.builder();
    breadcrumbs.add(breadcrumb(hostName, hostIndex().copyFrom(this).setRepositoryPrefix(null)));
    if (repositoryPrefix != null) {
      breadcrumbs.addAll(hostIndexBreadcrumbs(repositoryPrefix));
    } else if (repositoryName != null) {
      breadcrumbs.addAll(hostIndexBreadcrumbs(repositoryName));
    }
    if (type == Type.DIFF) {
      // TODO(dborowitz): Tweak the breadcrumbs template to allow us to render
      // separate links in "old..new".
      breadcrumbs.add(breadcrumb(getRevisionRange(), diff().copyFrom(this).setPathPart("")));
    } else if (type == Type.LOG) {
      if (revision != Revision.NULL) {
        // TODO(dborowitz): Add something in the navigation area (probably not
        // a breadcrumb) to allow switching between /+log/ and /+/.
        if (oldRevision == Revision.NULL) {
          breadcrumbs.add(breadcrumb(revision.getName(), log().copyFrom(this).setPathPart(null)));
        } else {
          breadcrumbs.add(breadcrumb(getRevisionRange(), log().copyFrom(this).setPathPart(null)));
        }
      } else {
        breadcrumbs.add(breadcrumb(Constants.HEAD, log().copyFrom(this)));
      }
      path = Strings.emptyToNull(path);
    } else if (revision != Revision.NULL) {
      breadcrumbs.add(breadcrumb(revision.getName(), revision().copyFrom(this)));
    }
    if (path != null) {
      if (type != Type.LOG && type != Type.REFS) {
        // The "." breadcrumb would be no different for LOG or REFS.
        breadcrumbs.add(breadcrumb(".", copyWithPath(false).setPathPart("")));
      }
      StringBuilder cur = new StringBuilder();
      List<String> parts = PathUtil.SPLITTER.omitEmptyStrings().splitToList(path);
      checkArgument(
          hasSingleTree == null
              || (parts.isEmpty() && hasSingleTree.isEmpty())
              || hasSingleTree.size() == parts.size() - 1,
          "hasSingleTree has wrong number of entries");
      for (int i = 0; i < parts.size(); i++) {
        String part = parts.get(i);
        cur.append(part).append('/');
        String curPath = cur.toString();
        boolean isLeaf = i == parts.size() - 1;
        Builder builder = copyWithPath(isLeaf).setPathPart(curPath);
        if (hasSingleTree != null && i < parts.size() - 1 && hasSingleTree.get(i)) {
          builder.replaceParam(PathServlet.AUTODIVE_PARAM, PathServlet.NO_AUTODIVE_VALUE);
        }
        breadcrumbs.add(breadcrumb(part, builder));
      }
    }
    return breadcrumbs.build();
  }

  private List<Map<String, String>> hostIndexBreadcrumbs(String name) {
    List<String> parts = Splitter.on('/').splitToList(name);
    List<Map<String, String>> r = new ArrayList<>(parts.size());
    for (int i = 0; i < parts.size(); i++) {
      String prefix = Joiner.on('/').join(parts.subList(0, i + 1));
      r.add(breadcrumb(parts.get(i), hostIndex().copyFrom(this).setRepositoryPrefix(prefix)));
    }
    return r;
  }

  private static Map<String, String> breadcrumb(String text, Builder url) {
    return ImmutableMap.of("text", text, "url", url.toUrl());
  }

  private Builder copyWithPath(boolean isLeaf) {
    Builder copy;
    switch (type) {
      case DIFF:
        copy = diff();
        break;
      case LOG:
        copy = log();
        break;
      case BLAME:
        copy = isLeaf ? blame() : path();
        break;
      case ARCHIVE:
      case DESCRIBE:
      case DOC:
      case HOST_INDEX:
      case PATH:
      case REFS:
      case REPOSITORY_INDEX:
      case REVISION:
      case ROOTED_DOC:
      case SHOW:
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

  @VisibleForTesting
  static String paramsToString(ListMultimap<String, String> params) {
    try {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (Map.Entry<String, String> e : params.entries()) {
        if (!first) {
          sb.append('&');
        } else {
          first = false;
        }
        sb.append(URLEncoder.encode(e.getKey(), UTF_8.name()));
        if (!"".equals(e.getValue())) {
          sb.append('=').append(URLEncoder.encode(e.getValue(), UTF_8.name()));
        }
      }
      return sb.toString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
