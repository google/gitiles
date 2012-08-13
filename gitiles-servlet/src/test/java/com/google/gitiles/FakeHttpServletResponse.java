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

import static com.google.common.base.Charsets.UTF_8;

import java.io.PrintWriter;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/** Simple fake implementation of {@link HttpServletResponse}. */
public class FakeHttpServletResponse implements HttpServletResponse {

  private volatile int status;

  public FakeHttpServletResponse() {
    status = 200;
  }

  @Override
  public void flushBuffer() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getBufferSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getCharacterEncoding() {
    return UTF_8.name();
  }

  @Override
  public String getContentType() {
    return null;
  }

  @Override
  public Locale getLocale() {
    return Locale.US;
  }

  @Override
  public ServletOutputStream getOutputStream() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrintWriter getWriter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCommitted() {
    return false;
  }

  @Override
  public void reset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resetBuffer() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBufferSize(int sz) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCharacterEncoding(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setContentLength(int length) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setContentType(String type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setLocale(Locale locale) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addCookie(Cookie cookie) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDateHeader(String name, long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addHeader(String name, String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addIntHeader(String name, int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsHeader(String name) {
    return false;
  }

  @Override
  public String encodeRedirectURL(String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public String encodeRedirectUrl(String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String encodeURL(String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public String encodeUrl(String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void sendError(int sc) {
    status = sc;
  }

  @Override
  public void sendError(int sc, String msg) {
    status = sc;
  }

  @Override
  public void sendRedirect(String msg) {
    status = SC_FOUND;
  }

  @Override
  public void setDateHeader(String name, long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setHeader(String name, String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setIntHeader(String name, int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setStatus(int sc) {
    status = sc;
  }

  @Override
  @Deprecated
  public void setStatus(int sc, String msg) {
    status = sc;
  }

  public int getStatus() {
    return status;
  }
}
