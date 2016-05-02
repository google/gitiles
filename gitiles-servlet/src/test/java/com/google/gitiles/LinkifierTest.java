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

import com.google.common.collect.ImmutableMap;

import org.eclipse.jgit.lib.Config;
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
    Config config = new Config();
    Linkifier l = new Linkifier(TestGitilesUrls.URLS, config);
    assertThat(l.linkify(FakeHttpServletRequest.newRequest(), "some message text"))
        .containsExactly(ImmutableMap.of("text", "some message text"));
  }

  @Test
  public void linkifyMessageUrl() throws Exception {
    Config config = new Config();
    Linkifier l = new Linkifier(TestGitilesUrls.URLS, config);
    assertThat(l.linkify(REQ, "http://my/url"))
        .containsExactly(ImmutableMap.of("text", "http://my/url", "url", "http://my/url"))
        .inOrder();
    assertThat(l.linkify(REQ, "https://my/url"))
        .containsExactly(ImmutableMap.of("text", "https://my/url", "url", "https://my/url"))
        .inOrder();
    assertThat(l.linkify(REQ, "foo http://my/url bar"))
        .containsExactly(
            ImmutableMap.of("text", "foo "),
            ImmutableMap.of("text", "http://my/url", "url", "http://my/url"),
            ImmutableMap.of("text", " bar"))
        .inOrder();
    assertThat(l.linkify(REQ, "foo http://my/url bar http://my/other/url baz"))
        .containsExactly(
            ImmutableMap.of("text", "foo "),
            ImmutableMap.of("text", "http://my/url", "url", "http://my/url"),
            ImmutableMap.of("text", " bar "),
            ImmutableMap.of("text", "http://my/other/url", "url", "http://my/other/url"),
            ImmutableMap.of("text", " baz"))
        .inOrder();
  }

  @Test
  public void linkifyMessageChangeIdNoGerrit() throws Exception {
    Config config = new Config();
    Linkifier l =
        new Linkifier(
            new GitilesUrls() {
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
            },
            config);
    assertThat(l.linkify(REQ, "I0123456789"))
        .containsExactly(ImmutableMap.of("text", "I0123456789"))
        .inOrder();
    assertThat(l.linkify(REQ, "Change-Id: I0123456789"))
        .containsExactly(ImmutableMap.of("text", "Change-Id: I0123456789"))
        .inOrder();
    assertThat(l.linkify(REQ, "Change-Id: I0123456789 does not exist"))
        .containsExactly(ImmutableMap.of("text", "Change-Id: I0123456789 does not exist"))
        .inOrder();
  }

  @Test
  public void linkifyMessageChangeId() throws Exception {
    Config config = new Config();
    Linkifier l = new Linkifier(TestGitilesUrls.URLS, config);
    assertThat(l.linkify(REQ, "I0123456789"))
        .containsExactly(
            ImmutableMap.of(
                "text", "I0123456789", "url", "http://test-host-review/foo/#/q/I0123456789,n,z"))
        .inOrder();
    assertThat(l.linkify(REQ, "Change-Id: I0123456789"))
        .containsExactly(
            ImmutableMap.of("text", "Change-Id: "),
            ImmutableMap.of(
                "text", "I0123456789", "url", "http://test-host-review/foo/#/q/I0123456789,n,z"))
        .inOrder();
    assertThat(l.linkify(REQ, "Change-Id: I0123456789 exists"))
        .containsExactly(
            ImmutableMap.of("text", "Change-Id: "),
            ImmutableMap.of(
                "text", "I0123456789", "url", "http://test-host-review/foo/#/q/I0123456789,n,z"),
            ImmutableMap.of("text", " exists"))
        .inOrder();
  }

  @Test
  public void linkifyMessageCommentLinks() throws Exception {
    Config config = new Config();
    config.setString("commentlink", "buglink", "match", "(bug\\s+#?)(\\d+)");
    config.setString("commentlink", "buglink", "link", "http://bugs/$2");
    config.setString("commentlink", "featurelink", "match", "(Feature:\\s+)(\\d+)");
    config.setString("commentlink", "featurelink", "link", "http://features/$2");
    Linkifier l = new Linkifier(TestGitilesUrls.URLS, config);
    assertThat(
            l.linkify(REQ, "There is a new Feature: 103, which is similar to the reported bug 100"))
        .containsExactly(
            ImmutableMap.of("text", "There is a new "),
            ImmutableMap.of("text", "Feature: 103", "url", "http://features/103"),
            ImmutableMap.of("text", ", which is similar to the reported "),
            ImmutableMap.of("text", "bug 100", "url", "http://bugs/100"))
        .inOrder();
  }

  @Test
  public void linkifyMessageUrlAndChangeId() throws Exception {
    Config config = new Config();
    Linkifier l = new Linkifier(TestGitilesUrls.URLS, config);
    assertThat(l.linkify(REQ, "http://my/url/I0123456789 is not change I0123456789"))
        .containsExactly(
            ImmutableMap.of(
                "text", "http://my/url/I0123456789", "url", "http://my/url/I0123456789"),
            ImmutableMap.of("text", " is not change "),
            ImmutableMap.of(
                "text", "I0123456789", "url", "http://test-host-review/foo/#/q/I0123456789,n,z"))
        .inOrder();
  }

  @Test
  public void linkifyAmpersand() throws Exception {
    Config config = new Config();
    Linkifier l = new Linkifier(TestGitilesUrls.URLS, config);
    assertThat(l.linkify(REQ, "http://my/url?a&b"))
        .containsExactly(ImmutableMap.of("text", "http://my/url?a&b", "url", "http://my/url?a&b"))
        .inOrder();
    assertThat(l.linkify(REQ, "http://weird/htmlified/?url&lt;p&rt;"))
        .containsExactly(
            ImmutableMap.of(
                "text", "http://weird/htmlified/?url", "url", "http://weird/htmlified/?url"),
            ImmutableMap.of("text", "&lt;p&rt;"))
        .inOrder();
  }

  @Test
  public void invalidCommentlinkMatchRegex() throws Exception {
    Config config = new Config();
    config.setString("commentlink", "foo", "match", "bad-regex(");
    new Linkifier(TestGitilesUrls.URLS, config);
    // Must not throw an exception
  }
}
