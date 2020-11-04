// Copyright 2018 Google Inc. All Rights Reserved.
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

package com.google.gitiles.doc;

/**
 * Constant definitions for Soy utilities.
 *
 * <p>These constant definitions are copied from Soy internal packages, and are repeated here at the
 * request of Soy team to limit our dependencies on their internal packages. The text still refers
 * to "zSoyz" for consistency with other non-Markdown parts of Gitiles.
 */
final class SoyConstants {
  /**
   * Innocuous output when an image data URI fails escaping.
   *
   * <p>When escaper fails, we need to return something that is both clearly an image, but clearly
   * invalid. We don't want the browser to fetch anything. We also don't necessarily want a
   * transparent gif, since it doesn't alert developers to an issue. And finally, by not starting
   * with GIF89a, we ensure the browser doesn't attempt to actually decode it and crash.
   *
   * <p>Based on {@link
   * com.google.template.soy.shared.internal.EscapingConventions.FilterImageDataUri}.
   */
  static final String IMAGE_URI_INNOCUOUS_OUTPUT = "data:image/gif;base64,zSoyz";

  /**
   * Innocuous output when a URI fails escaping.
   *
   * <p>about:invalid is registered in http://www.w3.org/TR/css3-values/#about-invalid: "The
   * about:invalid URI references a non-existent document with a generic error condition. It can be
   * used when a URI is necessary, but the default value shouldn't be resolveable as any type of
   * document."
   *
   * <p>Based on {@link
   * com.google.template.soy.shared.internal.EscapingConventions.FilterNormalizeUri}.
   */
  static final String NORMAL_URI_INNOCUOUS_OUTPUT = "about:invalid#zSoyz";

  private SoyConstants() {}
}
