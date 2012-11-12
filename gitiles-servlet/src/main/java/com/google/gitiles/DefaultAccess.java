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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.IO;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * Default implementation of {@link GitilesAccess} with local repositories.
 * <p>
 * Repositories are scanned on-demand under the given path, configured by
 * default from {@code gitiles.basePath}. There is no access control beyond what
 * user the JVM is running under.
 */
public class DefaultAccess implements GitilesAccess {
  private static final String ANONYMOUS_USER_KEY = "anonymous user";

  public static class Factory implements GitilesAccess.Factory {
    private final File basePath;
    private final String canonicalBasePath;
    private final String baseGitUrl;
    private final FileResolver<HttpServletRequest> resolver;

    Factory(File basePath, String baseGitUrl, FileResolver<HttpServletRequest> resolver)
        throws IOException {
      this.basePath = checkNotNull(basePath, "basePath");
      this.baseGitUrl = checkNotNull(baseGitUrl, "baseGitUrl");
      this.resolver = checkNotNull(resolver, "resolver");
      this.canonicalBasePath = basePath.getCanonicalPath();
    }

    @Override
    public GitilesAccess forRequest(HttpServletRequest req) {
      return newAccess(basePath, canonicalBasePath, baseGitUrl, resolver, req);
    }

    protected DefaultAccess newAccess(File basePath, String canonicalBasePath, String baseGitUrl,
        FileResolver<HttpServletRequest> resolver, HttpServletRequest req) {
      return new DefaultAccess(basePath, canonicalBasePath, baseGitUrl, resolver, req);
    }
  }

  protected final File basePath;
  protected final String canonicalBasePath;
  protected final String baseGitUrl;
  protected final FileResolver<HttpServletRequest> resolver;
  protected final HttpServletRequest req;

  protected DefaultAccess(File basePath, String canonicalBasePath, String baseGitUrl,
      FileResolver<HttpServletRequest> resolver, HttpServletRequest req) {
    this.basePath = checkNotNull(basePath, "basePath");
    this.canonicalBasePath = checkNotNull(canonicalBasePath, "canonicalBasePath");
    this.baseGitUrl = checkNotNull(baseGitUrl, "baseGitUrl");
    this.resolver = checkNotNull(resolver, "resolver");
    this.req = checkNotNull(req, "req");
  }

  @Override
  public Map<String, RepositoryDescription> listRepositories(Set<String> branches)
      throws IOException {
    Map<String, RepositoryDescription> repos = Maps.newTreeMap();
    for (Repository repo : scanRepositories(basePath, req)) {
      repos.put(getRepositoryName(repo), buildDescription(repo, branches));
      repo.close();
    }
    return repos;
  }

  @Override
  public Object getUserKey() {
    // Always return the same anonymous user key (effectively running with the
    // same user permissions as the JVM). Subclasses may override this behavior.
    return ANONYMOUS_USER_KEY;
  }

  @Override
  public String getRepositoryName() {
    return getRepositoryName(ServletUtils.getRepository(req));
  }

  @Override
  public RepositoryDescription getRepositoryDescription() throws IOException {
    return buildDescription(ServletUtils.getRepository(req), Collections.<String> emptySet());
  }

  private String getRepositoryName(Repository repo) {
    String path = getRelativePath(repo);
    if (repo.isBare() && path.endsWith(".git")) {
      path = path.substring(0, path.length() - 4);
    }
    return path;
  }

  private String getRelativePath(Repository repo) {
    String path = repo.isBare() ? repo.getDirectory().getPath() : repo.getDirectory().getParent();
    if (repo.isBare()) {
      path = repo.getDirectory().getPath();
      if (path.endsWith(".git")) {
        path = path.substring(0, path.length() - 4);
      }
    } else {
      path = repo.getDirectory().getParent();
    }
    return getRelativePath(path);
  }

  private String getRelativePath(String path) {
    String base = basePath.getPath();
    if (path.startsWith(base)) {
      return path.substring(base.length() + 1);
    }
    if (path.startsWith(canonicalBasePath)) {
      return path.substring(canonicalBasePath.length() + 1);
    }
    throw new IllegalStateException(String.format(
          "Repository path %s is outside base path %s", path, base));
  }

  private String loadDescriptionText(Repository repo) throws IOException {
    String desc = null;
    StoredConfig config = repo.getConfig();
    IOException configError = null;
    try {
      config.load();
      desc = config.getString("gitweb", null, "description");
    } catch (ConfigInvalidException e) {
      configError = new IOException(e);
    }
    if (desc == null) {
      File descFile = new File(repo.getDirectory(), "description");
      if (descFile.exists()) {
        desc = new String(IO.readFully(descFile));
      } else if (configError != null) {
        throw configError;
      }
    }
    return desc;
  }

  private RepositoryDescription buildDescription(Repository repo, Set<String> branches)
      throws IOException {
    RepositoryDescription desc = new RepositoryDescription();
    desc.name = getRepositoryName(repo);
    desc.cloneUrl = baseGitUrl + getRelativePath(repo);
    desc.description = loadDescriptionText(repo);
    if (!branches.isEmpty()) {
      desc.branches = Maps.newLinkedHashMap();
      for (String name : branches) {
        Ref ref = repo.getRef(normalizeRefName(name));
        if ((ref != null) && (ref.getObjectId() != null)) {
          desc.branches.put(name, ref.getObjectId().name());
        }
      }
    }
    return desc;
  }

  private static String normalizeRefName(String name) {
    if (name.startsWith("refs/")) {
      return name;
    }
    return "refs/heads/" + name;
  }

  private Collection<Repository> scanRepositories(final File basePath, final HttpServletRequest req) throws IOException {
    List<Repository> repos = Lists.newArrayList();
    Queue<File> todo = Queues.newArrayDeque();
    File[] baseFiles = basePath.listFiles();
    if (baseFiles == null) {
      throw new IOException("base path is not a directory: " + basePath.getPath());
    }
    todo.addAll(Arrays.asList(baseFiles));
    while (!todo.isEmpty()) {
      File file = todo.remove();
      try {
        repos.add(resolver.open(req, getRelativePath(file.getPath())));
      } catch (RepositoryNotFoundException e) {
        File[] children = file.listFiles();
        if (children != null) {
          todo.addAll(Arrays.asList(children));
        }
      } catch (ServiceNotEnabledException e) {
        throw new IOException(e);
      }
    }
    return repos;
  }
}
