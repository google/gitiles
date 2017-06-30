// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.html.types.SafeHtml;
import javax.servlet.http.HttpServletRequest;

/** Verifies a user content HTML block is safe. */
public interface HtmlSanitizer {
  public static final HtmlSanitizer DISABLED = unused -> SafeHtml.EMPTY;
  public static final Factory DISABLED_FACTORY = req -> DISABLED;

  /** Verifies the supplied block is safe, or returns {@link SafeHtml#EMPTY}. */
  SafeHtml sanitize(String html);

  /** Creates an {@link HtmlSanitizer} for this request. */
  public interface Factory {
    HtmlSanitizer create(HttpServletRequest req);
  }
}
