// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles.doc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gitiles.doc.PathResolver.resolve;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PathResolverTest {
  @Test
  public void resolveTests() {
    assertThat(resolve(null, "/foo.md")).isEqualTo("foo.md");
    assertThat(resolve(null, "////foo.md")).isEqualTo("foo.md");
    assertThat(resolve("/index.md", "/foo.md")).isEqualTo("foo.md");
    assertThat(resolve("index.md", "/foo.md")).isEqualTo("foo.md");
    assertThat(resolve("index.md", "foo.md")).isEqualTo("foo.md");
    assertThat(resolve(null, "foo.md")).isNull();

    assertThat(resolve("doc/index.md", "../foo.md")).isEqualTo("foo.md");
    assertThat(resolve("/doc/index.md", "../foo.md")).isEqualTo("foo.md");
    assertThat(resolve("/doc/index.md", ".././foo.md")).isEqualTo("foo.md");
    assertThat(resolve("/a/b/c/index.md", "../../foo.md")).isEqualTo("a/foo.md");
    assertThat(resolve("/a/index.md", "../../../foo.md")).isNull();
  }
}
