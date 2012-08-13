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

import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * Git storage interface for Gitiles.
 * <p>
 * Each instance is associated with a single end-user request, which implicitly
 * includes information about the host and repository.
 */
public interface GitilesAccess {
  /** Factory for per-request access. */
  public interface Factory {
    public GitilesAccess forRequest(HttpServletRequest req);
  }

  /**
   * List repositories on the host.
   *
   * @param branches branches to list along with each repository.
   * @return map of repository names to descriptions.
   * @throws ServiceNotEnabledException to trigger an HTTP 403 Forbidden
   *     (matching behavior in {@link org.eclipse.jgit.http.server.RepositoryFilter}).
   * @throws ServiceNotAuthorizedException to trigger an HTTP 401 Unauthorized
   *     (matching behavior in {@link org.eclipse.jgit.http.server.RepositoryFilter}).
   * @throws IOException if an error occurred.
   */
  public Map<String, RepositoryDescription> listRepositories(Set<String> branches)
      throws ServiceNotEnabledException, ServiceNotAuthorizedException, IOException;

  /**
   * @return an opaque object that uniquely identifies the end-user making the
   *     request, and supports {@link #equals(Object)} and {@link #hashCode()}.
   *     Never null.
   */
  public Object getUserKey();

  /** @return the repository name associated with the request. */
  public String getRepositoryName();

  /**
   * @return the description attached to the repository of this request.
   * @throws IOException an error occurred reading the description string from
   *         the repository.
   */
  public RepositoryDescription getRepositoryDescription() throws IOException;
}
