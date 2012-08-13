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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import javax.servlet.http.HttpServletRequest;

/** Tests for {@link Linkifier}. */
public class LinkifierTest extends TestCase {
  private static final HttpServletRequest REQ = FakeHttpServletRequest.newRequest();

  @Override
  protected void setUp() throws Exception {
  }

  public void testlinkifyMessageNoMatch() throws Exception {
    Linkifier l = new Linkifier(TestGitilesUrls.URLS);
    assertEquals(ImmutableList.of(ImmutableMap.of("text", "some message text")),
        l.linkify(FakeHttpServletRequest.newRequest(), "some message text"));
  }

  public void testlinkifyMessageUrl() throws Exception {
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

  public void testlinkifyMessageChangeIdNoGerrit() throws Exception {
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

  public void testlinkifyMessageChangeId() throws Exception {
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

  public void testlinkifyMessageUrlAndChangeId() throws Exception {
    Linkifier l = new Linkifier(TestGitilesUrls.URLS);
    assertEquals(ImmutableList.of(
        ImmutableMap.of("text", "http://my/url/I0123456789", "url", "http://my/url/I0123456789"),
        ImmutableMap.of("text", " is not change "),
        ImmutableMap.of("text", "I0123456789",
          "url", "http://test-host-review/foo/#/q/I0123456789,n,z")),
        l.linkify(REQ, "http://my/url/I0123456789 is not change I0123456789"));
  }
}
