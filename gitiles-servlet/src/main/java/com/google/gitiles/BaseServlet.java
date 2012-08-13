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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;

import org.joda.time.Instant;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Base servlet class for Gitiles servlets that serve Soy templates. */
public abstract class BaseServlet extends HttpServlet {
  private static final String DATA_ATTRIBUTE = BaseServlet.class.getName() + "/Data";

  static void setNotCacheable(HttpServletResponse res) {
    res.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate");
    res.setHeader(HttpHeaders.PRAGMA, "no-cache");
    res.setHeader(HttpHeaders.EXPIRES, "Fri, 01 Jan 1990 00:00:00 GMT");
    res.setDateHeader(HttpHeaders.DATE, new Instant().getMillis());
  }

  public static BaseServlet notFoundServlet() {
    return new BaseServlet(null) {
      @Override
      public void service(HttpServletRequest req, HttpServletResponse res) {
        res.setStatus(SC_NOT_FOUND);
      }
    };
  }

  public static Map<String, String> menuEntry(String text, String url) {
    if (url != null) {
      return ImmutableMap.of("text", text, "url", url);
    } else {
      return ImmutableMap.of("text", text);
    }
  }

  protected static Map<String, Object> getData(HttpServletRequest req) {
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) req.getAttribute(DATA_ATTRIBUTE);
    if (data == null) {
      data = Maps.newHashMap();
      req.setAttribute(DATA_ATTRIBUTE, data);
    }
    return data;
  }

  protected final Renderer renderer;

  protected BaseServlet(Renderer renderer) {
    this.renderer = renderer;
  }

  /**
   * Put a value into a request's Soy data map.
   * <p>
   * This method is intended to support a composition pattern whereby a
   * {@link BaseServlet} is wrapped in a different {@link HttpServlet} that can
   * update its data map.
   *
   * @param req in-progress request.
   * @param key key.
   * @param value Soy data value.
   */
  public void put(HttpServletRequest req, String key, Object value) {
    getData(req).put(key, value);
  }

  protected void render(HttpServletRequest req, HttpServletResponse res, String templateName,
      Map<String, ?> soyData) throws IOException {
    try {
      res.setContentType(FormatType.HTML.getMimeType());
      res.setCharacterEncoding(Charsets.UTF_8.name());
      setCacheHeaders(req, res);

      Map<String, Object> allData = getData(req);
      allData.putAll(soyData);
      GitilesView view = ViewFilter.getView(req);
      if (!allData.containsKey("repositoryName") && view.getRepositoryName() != null) {
        allData.put("repositoryName", view.getRepositoryName());
      }
      if (!allData.containsKey("breadcrumbs")) {
        allData.put("breadcrumbs", view.getBreadcrumbs());
      }

      res.setStatus(HttpServletResponse.SC_OK);
      renderer.render(res, templateName, allData);
    } finally {
      req.removeAttribute(DATA_ATTRIBUTE);
    }
  }

  protected void setCacheHeaders(HttpServletRequest req, HttpServletResponse res) {
    setNotCacheable(res);
  }
}
