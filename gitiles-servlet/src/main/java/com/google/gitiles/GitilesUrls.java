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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.servlet.http.HttpServletRequest;

/** Interface for URLs displayed on source browsing pages. */
public interface GitilesUrls {
  /**
   * Escapes repository or path names to be safely embedded into a URL.
   *
   * <p>This escape implementation escapes a repository or path name such as "foo/bar&lt;/child" to
   * appear as "foo/bar%3C/child". Spaces are escaped as "%20". Its purpose is to escape a
   * repository name to be safe for inclusion in the path component of the URL, where "/" is a valid
   * character that should not be encoded, while almost any other non-alpha, non-numeric character
   * will be encoded using URL style encoding.
   */
  static String escapeName(String name) {
    try {
      return URLEncoder.encode(name, UTF_8.name())
          .replace("%2F", "/")
          .replace("%2f", "/")
          .replace("+", "%20")
          .replace("%2B", "+")
          .replace("%2b", "+");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Return the name of the host from the request.
   *
   * <p>Used in various user-visible text, like "MyHost Git Repositories".
   *
   * @param req request.
   * @return host name; may be null.
   */
  String getHostName(HttpServletRequest req);

  /**
   * Return the base URL for git repositories on this host.
   *
   * @param req request.
   * @return base URL for git repositories.
   */
  String getBaseGitUrl(HttpServletRequest req);

  /**
   * Return the base URL for Gerrit projects on this host.
   *
   * @param req request.
   * @return base URL for Gerrit Code Review, or null if Gerrit is not configured.
   */
  String getBaseGerritUrl(HttpServletRequest req);
}
