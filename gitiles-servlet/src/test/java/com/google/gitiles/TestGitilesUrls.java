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

import javax.servlet.http.HttpServletRequest;

/** {@link GitilesUrls} for testing. */
public class TestGitilesUrls implements GitilesUrls {
  public static final GitilesUrls URLS = new TestGitilesUrls();
  public static final String HOST_NAME = "test-host";

  @Override
  public String getHostName(HttpServletRequest req) {
    return HOST_NAME;
  }

  @Override
  public String getBaseGitUrl(HttpServletRequest req) {
    return "git://" + HOST_NAME + "/foo";
  }

  @Override
  public String getBaseGerritUrl(HttpServletRequest req) {
    return "http://" + HOST_NAME + "-review/foo/";
  }

  private TestGitilesUrls() {}
}
