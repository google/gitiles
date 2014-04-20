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

import static com.google.gitiles.GitilesFilter.REPO_PATH_REGEX;
import static com.google.gitiles.GitilesFilter.REPO_REGEX;
import static com.google.gitiles.GitilesFilter.ROOT_REGEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Matcher;

/** Tests for the Gitiles filter. */
public class GitilesFilterTest {
  @Test
  public void rootUrls() throws Exception {
    assertFalse(ROOT_REGEX.matcher("").matches());
    assertFalse(ROOT_REGEX.matcher("/foo").matches());
    assertFalse(ROOT_REGEX.matcher("/foo/").matches());
    assertFalse(ROOT_REGEX.matcher("/foo/ ").matches());
    assertFalse(ROOT_REGEX.matcher("/foo/+").matches());
    assertFalse(ROOT_REGEX.matcher("/foo/+").matches());
    assertFalse(ROOT_REGEX.matcher("/foo/ /").matches());
    assertFalse(ROOT_REGEX.matcher("/foo/+/").matches());
    assertFalse(ROOT_REGEX.matcher("/foo/+/bar").matches());
    Matcher m;

    m = ROOT_REGEX.matcher("/");
    assertTrue(m.matches());
    assertEquals("/", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/", m.group(2));
    assertEquals("", m.group(3));
    assertEquals("", m.group(4));

    m = ROOT_REGEX.matcher("//");
    assertTrue(m.matches());
    assertEquals("//", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/", m.group(2));
    assertEquals("", m.group(3));
    assertEquals("", m.group(4));
  }

  @Test
  public void repoUrls() throws Exception {
    assertFalse(REPO_REGEX.matcher("").matches());

    // These match the regex but are served by the root regex binder, which is
    // matched first.
    assertTrue(REPO_REGEX.matcher("/").matches());
    assertTrue(REPO_REGEX.matcher("//").matches());

    assertFalse(REPO_REGEX.matcher("/foo/+").matches());
    assertFalse(REPO_REGEX.matcher("/foo/bar/+").matches());
    assertFalse(REPO_REGEX.matcher("/foo/bar/+/").matches());
    assertFalse(REPO_REGEX.matcher("/foo/bar/+/baz").matches());
    Matcher m;

    m = REPO_REGEX.matcher("/foo");
    assertTrue(m.matches());
    assertEquals("/foo", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("", m.group(3));
    assertEquals("", m.group(4));

    m = REPO_REGEX.matcher("/foo/");
    assertTrue(m.matches());
    assertEquals("/foo/", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("", m.group(3));
    assertEquals("", m.group(4));

    m = REPO_REGEX.matcher("/foo/bar");
    assertTrue(m.matches());
    assertEquals("/foo/bar", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo/bar", m.group(2));
    assertEquals("", m.group(3));
    assertEquals("", m.group(4));

    m = REPO_REGEX.matcher("/foo/bar+baz");
    assertTrue(m.matches());
    assertEquals("/foo/bar+baz", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo/bar+baz", m.group(2));
    assertEquals("", m.group(3));
    assertEquals("", m.group(4));
  }

  @Test
  public void repoPathUrls() throws Exception {
    assertFalse(REPO_PATH_REGEX.matcher("").matches());
    assertFalse(REPO_PATH_REGEX.matcher("/").matches());
    assertFalse(REPO_PATH_REGEX.matcher("//").matches());
    assertFalse(REPO_PATH_REGEX.matcher("/foo").matches());
    assertFalse(REPO_PATH_REGEX.matcher("/foo/ ").matches());
    assertFalse(REPO_PATH_REGEX.matcher("/foo/ /").matches());
    assertFalse(REPO_PATH_REGEX.matcher("/foo/ /bar").matches());
    assertFalse(REPO_PATH_REGEX.matcher("/foo/bar").matches());
    assertFalse(REPO_PATH_REGEX.matcher("/foo/bar+baz").matches());
    Matcher m;

    m = REPO_PATH_REGEX.matcher("/foo/+");
    assertTrue(m.matches());
    assertEquals("/foo/+", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("+", m.group(3));
    assertEquals("", m.group(4));

    m = REPO_PATH_REGEX.matcher("/foo/+/");
    assertTrue(m.matches());
    assertEquals("/foo/+/", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("+", m.group(3));
    assertEquals("/", m.group(4));

    m = REPO_PATH_REGEX.matcher("/foo/+/bar/baz");
    assertTrue(m.matches());
    assertEquals("/foo/+/bar/baz", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("+", m.group(3));
    assertEquals("/bar/baz", m.group(4));

    m = REPO_PATH_REGEX.matcher("/foo/+/bar/baz/");
    assertTrue(m.matches());
    assertEquals("/foo/+/bar/baz/", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("+", m.group(3));
    assertEquals("/bar/baz/", m.group(4));

    m = REPO_PATH_REGEX.matcher("/foo/+/bar baz");
    assertTrue(m.matches());
    assertEquals("/foo/+/bar baz", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("+", m.group(3));
    assertEquals("/bar baz", m.group(4));

    m = REPO_PATH_REGEX.matcher("/foo/+/bar/+/baz");
    assertTrue(m.matches());
    assertEquals("/foo/+/bar/+/baz", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("+", m.group(3));
    assertEquals("/bar/+/baz", m.group(4));

    m = REPO_PATH_REGEX.matcher("/foo/+bar/baz");
    assertTrue(m.matches());
    assertEquals("/foo/+bar/baz", m.group(0));
    assertEquals(m.group(0), m.group(1));
    assertEquals("/foo", m.group(2));
    assertEquals("+bar", m.group(3));
    assertEquals("/baz", m.group(4));
  }
}
