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

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.servlet.http.HttpServletRequest;

/**
 * Default implementation of {@link GitilesUrls}.
 *
 * <p>This implementation uses statically-configured defaults, and thus assumes that the servlet is
 * running a single virtual host.
 */
public class DefaultUrls implements GitilesUrls {
  private final String canonicalHostName;
  private final String baseGitUrl;
  private final String baseGerritUrl;

  public DefaultUrls(String canonicalHostName, String baseGitUrl, String baseGerritUrl)
      throws UnknownHostException {
    if (canonicalHostName != null) {
      this.canonicalHostName = canonicalHostName;
    } else {
      this.canonicalHostName = InetAddress.getLocalHost().getCanonicalHostName();
    }
    this.baseGitUrl = checkNotNull(baseGitUrl, "baseGitUrl");
    this.baseGerritUrl = baseGerritUrl;
  }

  @Override
  public String getHostName(HttpServletRequest req) {
    return canonicalHostName;
  }

  @Override
  public String getBaseGitUrl(HttpServletRequest req) {
    return baseGitUrl;
  }

  @Override
  public String getBaseGerritUrl(HttpServletRequest req) {
    return baseGerritUrl;
  }
}
