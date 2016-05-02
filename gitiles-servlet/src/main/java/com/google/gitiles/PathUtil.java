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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.Files;

import java.util.StringTokenizer;

/** Static utilities for dealing with pathnames. */
class PathUtil {
  private static final CharMatcher MATCHER = CharMatcher.is('/');
  static final Splitter SPLITTER = Splitter.on(MATCHER);

  static String simplifyPathUpToRoot(String path, String root) {
    if (path.startsWith("/")) {
      return null;
    }

    root = Strings.nullToEmpty(root);
    // simplifyPath() normalizes "a/../../" to "a", so manually check whether
    // the path leads above the root.
    int depth = new StringTokenizer(root, "/").countTokens();
    for (String part : SPLITTER.split(path)) {
      if (part.equals("..")) {
        depth--;
        if (depth < 0) {
          return null;
        }
      } else if (!part.isEmpty() && !part.equals(".")) {
        depth++;
      }
    }

    String result = Files.simplifyPath(!root.isEmpty() ? root + "/" + path : path);
    return !result.equals(".") ? result : "";
  }

  static String basename(String path) {
    path = MATCHER.trimTrailingFrom(path);
    int slash = path.lastIndexOf('/');
    if (slash < 0) {
      return path;
    }
    return path.substring(slash + 1);
  }

  private PathUtil() {}
}
