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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gitiles.GitilesFilter.REPO_PATH_REGEX;
import static com.google.gitiles.GitilesFilter.REPO_REGEX;
import static com.google.gitiles.GitilesFilter.ROOT_REGEX;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.regex.Matcher;

/** Tests for the Gitiles filter. */
@RunWith(JUnit4.class)
public class GitilesFilterTest {
  @Test
  public void rootUrls() throws Exception {
    assertThat("").doesNotMatch(ROOT_REGEX);
    assertThat("/foo").doesNotMatch(ROOT_REGEX);
    assertThat("/foo/").doesNotMatch(ROOT_REGEX);
    assertThat("/foo/ ").doesNotMatch(ROOT_REGEX);
    assertThat("/foo/+").doesNotMatch(ROOT_REGEX);
    assertThat("/foo/+").doesNotMatch(ROOT_REGEX);
    assertThat("/foo/ /").doesNotMatch(ROOT_REGEX);
    assertThat("/foo/+/").doesNotMatch(ROOT_REGEX);
    assertThat("/foo/+/bar").doesNotMatch(ROOT_REGEX);
    Matcher m;

    m = ROOT_REGEX.matcher("/");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/");
    assertThat(m.group(3)).isEqualTo("");
    assertThat(m.group(4)).isEqualTo("");

    m = ROOT_REGEX.matcher("//");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("//");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/");
    assertThat(m.group(3)).isEqualTo("");
    assertThat(m.group(4)).isEqualTo("");
  }

  @Test
  public void repoUrls() throws Exception {
    assertThat("").doesNotMatch(REPO_REGEX);

    // These match the regex but are served by the root regex binder, which is
    // matched first.
    assertThat("/").matches(REPO_REGEX);
    assertThat("//").matches(REPO_REGEX);

    assertThat("/foo/+").doesNotMatch(REPO_REGEX);
    assertThat("/foo/bar/+").doesNotMatch(REPO_REGEX);
    assertThat("/foo/bar/+/").doesNotMatch(REPO_REGEX);
    assertThat("/foo/bar/+/baz").doesNotMatch(REPO_REGEX);
    Matcher m;

    m = REPO_REGEX.matcher("/foo");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("");
    assertThat(m.group(4)).isEqualTo("");

    m = REPO_REGEX.matcher("/foo/");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("");
    assertThat(m.group(4)).isEqualTo("");

    m = REPO_REGEX.matcher("/foo/bar");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/bar");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo/bar");
    assertThat(m.group(3)).isEqualTo("");
    assertThat(m.group(4)).isEqualTo("");

    m = REPO_REGEX.matcher("/foo/bar+baz");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/bar+baz");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo/bar+baz");
    assertThat(m.group(3)).isEqualTo("");
    assertThat(m.group(4)).isEqualTo("");
  }

  @Test
  public void repoPathUrls() throws Exception {
    assertThat("").doesNotMatch(REPO_PATH_REGEX);
    assertThat("/").doesNotMatch(REPO_PATH_REGEX);
    assertThat("//").doesNotMatch(REPO_PATH_REGEX);
    assertThat("/foo").doesNotMatch(REPO_PATH_REGEX);
    assertThat("/foo/ ").doesNotMatch(REPO_PATH_REGEX);
    assertThat("/foo/ /").doesNotMatch(REPO_PATH_REGEX);
    assertThat("/foo/ /bar").doesNotMatch(REPO_PATH_REGEX);
    assertThat("/foo/bar").doesNotMatch(REPO_PATH_REGEX);
    assertThat("/foo/bar+baz").doesNotMatch(REPO_PATH_REGEX);
    Matcher m;

    m = REPO_PATH_REGEX.matcher("/foo/+");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/+");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("+");
    assertThat(m.group(4)).isEqualTo("");

    m = REPO_PATH_REGEX.matcher("/foo/+/");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/+/");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("+");
    assertThat(m.group(4)).isEqualTo("/");

    m = REPO_PATH_REGEX.matcher("/foo/+/bar/baz");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/+/bar/baz");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("+");
    assertThat(m.group(4)).isEqualTo("/bar/baz");

    m = REPO_PATH_REGEX.matcher("/foo/+/bar/baz/");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/+/bar/baz/");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("+");
    assertThat(m.group(4)).isEqualTo("/bar/baz/");

    m = REPO_PATH_REGEX.matcher("/foo/+/bar baz");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/+/bar baz");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("+");
    assertThat(m.group(4)).isEqualTo("/bar baz");

    m = REPO_PATH_REGEX.matcher("/foo/+/bar/+/baz");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/+/bar/+/baz");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("+");
    assertThat(m.group(4)).isEqualTo("/bar/+/baz");

    m = REPO_PATH_REGEX.matcher("/foo/+bar/baz");
    assertThat(m.matches()).isTrue();
    assertThat(m.group(0)).isEqualTo("/foo/+bar/baz");
    assertThat(m.group(1)).isEqualTo(m.group(0));
    assertThat(m.group(2)).isEqualTo("/foo");
    assertThat(m.group(3)).isEqualTo("+bar");
    assertThat(m.group(4)).isEqualTo("/baz");
  }
}
