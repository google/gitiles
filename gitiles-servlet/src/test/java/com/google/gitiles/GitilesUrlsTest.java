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
import static com.google.gitiles.GitilesUrls.NAME_ESCAPER;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GitilesUrls}. */
@RunWith(JUnit4.class)
public class GitilesUrlsTest {
  @Test
  public void nameEscaperEscapesAppropriateSpecialCharacters() throws Exception {
    assertEquals("foo_bar", NAME_ESCAPER.apply("foo_bar"));
    assertEquals("foo-bar", NAME_ESCAPER.apply("foo-bar"));
    assertEquals("foo%25bar", NAME_ESCAPER.apply("foo%bar"));
    assertEquals("foo%26bar", NAME_ESCAPER.apply("foo&bar"));
    assertEquals("foo%28bar", NAME_ESCAPER.apply("foo(bar"));
    assertEquals("foo%29bar", NAME_ESCAPER.apply("foo)bar"));
    assertEquals("foo%3Abar", NAME_ESCAPER.apply("foo:bar"));
    assertEquals("foo%3Bbar", NAME_ESCAPER.apply("foo;bar"));
    assertEquals("foo%3Dbar", NAME_ESCAPER.apply("foo=bar"));
    assertEquals("foo%3Fbar", NAME_ESCAPER.apply("foo?bar"));
    assertEquals("foo%5Bbar", NAME_ESCAPER.apply("foo[bar"));
    assertEquals("foo%5Dbar", NAME_ESCAPER.apply("foo]bar"));
    assertEquals("foo%7Bbar", NAME_ESCAPER.apply("foo{bar"));
    assertEquals("foo%7Dbar", NAME_ESCAPER.apply("foo}bar"));
  }

  @Test
  public void nameEscaperDoesNotEscapeSlashes() throws Exception {
    assertEquals("foo/bar", NAME_ESCAPER.apply("foo/bar"));
  }

  @Test
  public void nameEscaperEscapesSpacesWithPercentInsteadOfPlus() throws Exception {
    assertEquals("foo+bar", NAME_ESCAPER.apply("foo+bar"));
    assertEquals("foo%20bar", NAME_ESCAPER.apply("foo bar"));
    assertEquals("foo%2520bar", NAME_ESCAPER.apply("foo%20bar"));
  }
}
