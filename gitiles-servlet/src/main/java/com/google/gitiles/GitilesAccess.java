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

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Git storage interface for Gitiles.
 * <p>
 * Each instance is associated with a single end-user request, which implicitly
 * includes information about the host and repository.
 */
public interface GitilesAccess {
  /** Factory for per-request access. */
  public interface Factory {
    GitilesAccess forRequest(HttpServletRequest req);
  }

  /**
   * List repositories on the host.
   *
   * @param prefix repository base path to list. Trailing "/" is implicitly
   *        added if missing. Null or empty string will match all repositories.
   * @param branches branches to list along with each repository.
   * @return map of repository names to descriptions.
   * @throws ServiceNotEnabledException to trigger an HTTP 403 Forbidden
   *         (matching behavior in
   *         {@link org.eclipse.jgit.http.server.RepositoryFilter}).
   * @throws ServiceNotAuthorizedException to trigger an HTTP 401 Unauthorized
   *         (matching behavior in
   *         {@link org.eclipse.jgit.http.server.RepositoryFilter}).
   * @throws IOException if an error occurred.
   */
  Map<String, RepositoryDescription> listRepositories(
      @Nullable String prefix, Set<String> branches)
      throws ServiceNotEnabledException, ServiceNotAuthorizedException, IOException;

  /**
   * @return an opaque object that uniquely identifies the end-user making the
   *     request, and supports {@link Object#equals(Object)} and
   *     {@link Object#hashCode()}. Never null.
   */
  Object getUserKey();

  /** @return the repository name associated with the request. */
  String getRepositoryName();

  /**
   * @return the description attached to the repository of this request.
   * @throws IOException an error occurred reading the description string from
   *         the repository.
   */
  RepositoryDescription getRepositoryDescription() throws IOException;

  /**
   * @return configuration to apply to the host/repository for this request.
   * @throws IOException an error occurred reading the configuration.
   */
  Config getConfig() throws IOException;
}
