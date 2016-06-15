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

import com.google.common.base.Enums;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;

import javax.servlet.http.HttpServletRequest;

/** Type of formatting to use in the response to the client. */
public enum FormatType {
  HTML("text/html"),
  TEXT("text/plain"),
  JSON("application/json"),
  DEFAULT("*/*");

  private static final String FORMAT_TYPE_ATTRIBUTE = FormatType.class.getName();

  public static Optional<FormatType> getFormatType(HttpServletRequest req) {
    @SuppressWarnings("unchecked")
    Optional<FormatType> result = (Optional<FormatType>) req.getAttribute(FORMAT_TYPE_ATTRIBUTE);
    if (result != null) {
      return result;
    }

    String fmt = req.getParameter("format");
    if (!Strings.isNullOrEmpty(fmt)) {
      return set(req, Enums.getIfPresent(FormatType.class, fmt.toUpperCase()));
    }

    String accept = req.getHeader(HttpHeaders.ACCEPT);
    if (Strings.isNullOrEmpty(accept)) {
      return set(req, Optional.of(DEFAULT));
    }

    for (String p : accept.split("[ ,;][ ,;]*")) {
      for (FormatType type : FormatType.values()) {
        if (p.equals(type.mimeType)) {
          return set(req, Optional.of(type != HTML ? type : DEFAULT));
        }
      }
    }
    return set(req, Optional.of(DEFAULT));
  }

  private static Optional<FormatType> set(HttpServletRequest req, Optional<FormatType> format) {
    req.setAttribute(FORMAT_TYPE_ATTRIBUTE, format);
    return format;
  }

  private final String mimeType;

  FormatType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getMimeType() {
    return mimeType;
  }
}
