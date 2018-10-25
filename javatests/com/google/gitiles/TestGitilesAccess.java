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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.Config;

/** Gitiles access for testing. */
public class TestGitilesAccess implements GitilesAccess.Factory {
  private final DfsRepository repo;
  private final Config config;

  public TestGitilesAccess(DfsRepository repo) {
    this(repo, createDefaultConfig());
  }

  public TestGitilesAccess(DfsRepository repo, Config config) {
    this.repo = checkNotNull(repo);
    this.config = config;
  }

  @Override
  public GitilesAccess forRequest(final HttpServletRequest req) {
    return new GitilesAccess() {
      @Override
      public Map<String, RepositoryDescription> listRepositories(
          String prefix, Set<String> branches) {
        String name = repo.getDescription().getRepositoryName();
        if (prefix != null) {
          String pattern = CharMatcher.is('/').trimFrom(prefix) + '/';
          if (!name.startsWith(pattern)) {
            return Collections.emptyMap();
          }
        }
        if (branches != null && !branches.isEmpty()) {
          throw new UnsupportedOperationException("branches set not yet supported");
        }
        RepositoryDescription desc = new RepositoryDescription();
        desc.name = name;
        desc.cloneUrl = TestGitilesUrls.URLS.getBaseGitUrl(req) + "/" + desc.name;
        return ImmutableMap.of(desc.name, desc);
      }

      @Override
      public Object getUserKey() {
        return "a user";
      }

      @Override
      public String getRepositoryName() {
        return repo.getDescription().getRepositoryName();
      }

      @Override
      public RepositoryDescription getRepositoryDescription() {
        RepositoryDescription d = new RepositoryDescription();
        d.name = getRepositoryName();
        d.description = "a test data set";
        d.cloneUrl = TestGitilesUrls.URLS.getBaseGitUrl(req) + "/" + d.name;
        return d;
      }

      @Override
      public Config getConfig() {
        return new Config(config);
      }
    };
  }

  public static Config createDefaultConfig() {
    Config defaultConfig = new Config();
    defaultConfig.setBoolean("markdown", null, "blocknote", true);
    defaultConfig.setBoolean("markdown", null, "multicolumn", true);
    defaultConfig.setBoolean("markdown", null, "namedanchor", true);
    defaultConfig.setBoolean("markdown", null, "smartquote", true);
    defaultConfig.setStringList(
        "gitiles", null, "allowOriginRegex", ImmutableList.of("http://localhost"));
    return defaultConfig;
  }
}
