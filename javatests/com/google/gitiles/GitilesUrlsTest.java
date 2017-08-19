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
import static com.google.gitiles.GitilesUrls.escapeName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GitilesUrls}. */
@RunWith(JUnit4.class)
public class GitilesUrlsTest {
  @Test
  public void nameEscaperEscapesAppropriateSpecialCharacters() throws Exception {
    assertThat(escapeName("foo_bar")).isEqualTo("foo_bar");
    assertThat(escapeName("foo-bar")).isEqualTo("foo-bar");
    assertThat(escapeName("foo%bar")).isEqualTo("foo%25bar");
    assertThat(escapeName("foo&bar")).isEqualTo("foo%26bar");
    assertThat(escapeName("foo(bar")).isEqualTo("foo%28bar");
    assertThat(escapeName("foo)bar")).isEqualTo("foo%29bar");
    assertThat(escapeName("foo:bar")).isEqualTo("foo%3Abar");
    assertThat(escapeName("foo;bar")).isEqualTo("foo%3Bbar");
    assertThat(escapeName("foo=bar")).isEqualTo("foo%3Dbar");
    assertThat(escapeName("foo?bar")).isEqualTo("foo%3Fbar");
    assertThat(escapeName("foo[bar")).isEqualTo("foo%5Bbar");
    assertThat(escapeName("foo]bar")).isEqualTo("foo%5Dbar");
    assertThat(escapeName("foo{bar")).isEqualTo("foo%7Bbar");
    assertThat(escapeName("foo}bar")).isEqualTo("foo%7Dbar");
  }

  @Test
  public void nameEscaperDoesNotEscapeSlashes() throws Exception {
    assertThat(escapeName("foo/bar")).isEqualTo("foo/bar");
  }

  @Test
  public void nameEscaperEscapesSpacesWithPercentInsteadOfPlus() throws Exception {
    assertThat(escapeName("foo+bar")).isEqualTo("foo+bar");
    assertThat(escapeName("foo bar")).isEqualTo("foo%20bar");
    assertThat(escapeName("foo%20bar")).isEqualTo("foo%2520bar");
  }
}
