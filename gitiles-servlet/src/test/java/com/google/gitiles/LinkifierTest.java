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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.servlet.http.HttpServletRequest;

/** Tests for {@link Linkifier}. */
@RunWith(JUnit4.class)
public class LinkifierTest {
  private static final HttpServletRequest REQ = FakeHttpServletRequest.newRequest();

  @Test
  public void linkifyMessageNoMatch() throws Exception {
    Linkifier l = new Linkifier(TestGitilesUrls.URLS);
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "some message text")),
        l.linkify(FakeHttpServletRequest.newRequest(), "some message text"));
  }

  @Test
  public void linkifyMessageUrl() throws Exception {
    Linkifier l = new Linkifier(TestGitilesUrls.URLS);
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "http://my/url", "url", "http://my/url")),
        l.linkify(REQ, "http://my/url"));
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "https://my/url", "url", "https://my/url")),
        l.linkify(REQ, "https://my/url"));
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "foo "),
        ImmutableMap.of("text", "http://my/url", "url", "http://my/url"),
        ImmutableMap.of("text", " bar")),
        l.linkify(REQ, "foo http://my/url bar"));
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "foo "),
        ImmutableMap.of("text", "http://my/url", "url", "http://my/url"),
        ImmutableMap.of("text", " bar "),
        ImmutableMap.of("text", "http://my/other/url", "url", "http://my/other/url"),
        ImmutableMap.of("text", " baz")),
        l.linkify(REQ, "foo http://my/url bar http://my/other/url baz"));
  }

  @Test
  public void linkifyMessageChangeIdNoGerrit() throws Exception {
    Linkifier l = new Linkifier(new GitilesUrls() {
      @Override
      public String getBaseGerritUrl(HttpServletRequest req) {
        return null;
      }

      @Override
      public String getHostName(HttpServletRequest req) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String getBaseGitUrl(HttpServletRequest req) {
        throw new UnsupportedOperationException();
      }
    });
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "I0123456789")),
        l.linkify(REQ, "I0123456789"));
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "Change-Id: I0123456789")),
        l.linkify(REQ, "Change-Id: I0123456789"));
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "Change-Id: I0123456789 does not exist")),
        l.linkify(REQ, "Change-Id: I0123456789 does not exist"));
  }

  @Test
  public void linkifyMessageChangeId() throws Exception {
    Linkifier l = new Linkifier(TestGitilesUrls.URLS);
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "I0123456789",
          "url", "http://test-host-review/foo/#/q/I0123456789,n,z")),
        l.linkify(REQ, "I0123456789"));
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "Change-Id: "),
        ImmutableMap.of("text", "I0123456789",
          "url", "http://test-host-review/foo/#/q/I0123456789,n,z")),
        l.linkify(REQ, "Change-Id: I0123456789"));
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "Change-Id: "),
        ImmutableMap.of("text", "I0123456789",
          "url", "http://test-host-review/foo/#/q/I0123456789,n,z"),
        ImmutableMap.of("text", " exists")),
        l.linkify(REQ, "Change-Id: I0123456789 exists"));
  }

  @Test
  public void linkifyMessageUrlAndChangeId() throws Exception {
    Linkifier l = new Linkifier(TestGitilesUrls.URLS);
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "http://my/url/I0123456789", "url", "http://my/url/I0123456789"),
        ImmutableMap.of("text", " is not change "),
        ImmutableMap.of("text", "I0123456789",
          "url", "http://test-host-review/foo/#/q/I0123456789,n,z")),
        l.linkify(REQ, "http://my/url/I0123456789 is not change I0123456789"));
  }

  @Test
  public void linkifyAmpersand() throws Exception {
    Linkifier l = new Linkifier(TestGitilesUrls.URLS);
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "http://my/url?a&b", "url", "http://my/url?a&b")),
        l.linkify(REQ, "http://my/url?a&b"));
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "http://weird/htmlified/?url",
          "url", "http://weird/htmlified/?url"),
        ImmutableMap.of("text", "&lt;p&rt;")),
        l.linkify(REQ, "http://weird/htmlified/?url&lt;p&rt;"));
  }
}
