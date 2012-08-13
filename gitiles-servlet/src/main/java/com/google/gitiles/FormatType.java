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

  public static FormatType getFormatType(HttpServletRequest req) {
    FormatType result = (FormatType) req.getAttribute(FORMAT_TYPE_ATTRIBUTE);
    if (result != null) {
      return result;
    }

    String format = req.getParameter("format");
    if (format != null) {
      for (FormatType type : FormatType.values()) {
        if (format.equalsIgnoreCase(type.name())) {
          return set(req, type);
        }
      }
      throw new IllegalArgumentException("Invalid format " + format);
    }

    String accept = req.getHeader(HttpHeaders.ACCEPT);
    if (Strings.isNullOrEmpty(accept)) {
      return set(req, DEFAULT);
    }

    for (String p : accept.split("[ ,;][ ,;]*")) {
      for (FormatType type : FormatType.values()) {
        if (p.equals(type.mimeType)) {
          return set(req, type != HTML ? type : DEFAULT);
        }
      }
    }
    return set(req, DEFAULT);
  }

  private static FormatType set(HttpServletRequest req, FormatType format) {
    req.setAttribute(FORMAT_TYPE_ATTRIBUTE, format);
    return format;
  }

  private final String mimeType;

  private FormatType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getMimeType() {
    return mimeType;
  }
}
