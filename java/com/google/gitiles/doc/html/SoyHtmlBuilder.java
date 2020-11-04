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

package com.google.gitiles.doc.html;

import com.google.common.html.types.LegacyConversions;
import com.google.common.html.types.SafeHtml;

/** Builds a document fragment using a restricted subset of HTML. */
public final class SoyHtmlBuilder extends HtmlBuilder {
  private final StringBuilder buf;

  public SoyHtmlBuilder() {
    this(new StringBuilder());
  }

  private SoyHtmlBuilder(StringBuilder buf) {
    super(buf);
    this.buf = buf;
  }

  /** Bless the current content as HTML. */
  public SafeHtml toSoy() {
    finish();
    return LegacyConversions.riskilyAssumeSafeHtml(buf.toString());
  }
}
