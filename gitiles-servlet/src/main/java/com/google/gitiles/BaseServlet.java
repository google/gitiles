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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gitiles.FormatType.DEFAULT;
import static com.google.gitiles.FormatType.HTML;
import static com.google.gitiles.FormatType.JSON;
import static com.google.gitiles.FormatType.TEXT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;

import org.joda.time.Instant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Base servlet class for Gitiles servlets that serve Soy templates. */
public abstract class BaseServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final String ACCESS_ATTRIBUTE = BaseServlet.class.getName() + "/GitilesAccess";
  private static final String DATA_ATTRIBUTE = BaseServlet.class.getName() + "/Data";
  private static final String STREAMING_ATTRIBUTE = BaseServlet.class.getName() + "/Streaming";

  static void setNotCacheable(HttpServletResponse res) {
    res.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate");
    res.setHeader(HttpHeaders.PRAGMA, "no-cache");
    res.setHeader(HttpHeaders.EXPIRES, "Mon, 01 Jan 1990 00:00:00 GMT");
    res.setDateHeader(HttpHeaders.DATE, new Instant().getMillis());
  }

  public static BaseServlet notFoundServlet() {
    return new BaseServlet(null, null) {
      private static final long serialVersionUID = 1L;

      @Override
      public void service(HttpServletRequest req, HttpServletResponse res) {
        res.setStatus(SC_NOT_FOUND);
      }
    };
  }

  public static Map<String, String> menuEntry(String text, String url) {
    if (url != null) {
      return ImmutableMap.of("text", text, "url", url);
    }
    return ImmutableMap.of("text", text);
  }

  public static boolean isStreamingResponse(HttpServletRequest req) {
    return firstNonNull((Boolean) req.getAttribute(STREAMING_ATTRIBUTE), false);
  }

  protected static ArchiveFormat getArchiveFormat(GitilesAccess access) throws IOException {
    return ArchiveFormat.getDefault(access.getConfig());
  }

  /**
   * Put a value into a request's Soy data map.
   *
   * @param req in-progress request.
   * @param key key.
   * @param value Soy data value.
   */
  public static void putSoyData(HttpServletRequest req, String key, Object value) {
    getData(req).put(key, value);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    Optional<FormatType> format = getFormat(req);
    if (!format.isPresent()) {
      res.sendError(SC_BAD_REQUEST);
      return;
    }
    switch (format.get()) {
      case HTML:
        doGetHtml(req, res);
        break;
      case TEXT:
        doGetText(req, res);
        break;
      case JSON:
        doGetJson(req, res);
        break;
      case DEFAULT:
      default:
        res.sendError(SC_BAD_REQUEST);
        break;
    }
  }

  protected Optional<FormatType> getFormat(HttpServletRequest req) {
    Optional<FormatType> format = FormatType.getFormatType(req);
    if (format.isPresent() && format.get() == DEFAULT) {
      return Optional.of(getDefaultFormat(req));
    }
    return format;
  }

  /**
   * @param req in-progress request.
   * @return the default {@link FormatType} used when {@code ?format=} is not
   *     specified.
   */
  protected FormatType getDefaultFormat(HttpServletRequest req) {
    return HTML;
  }

  /**
   * Handle a GET request when the requested format type was HTML.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   */
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    res.sendError(SC_BAD_REQUEST);
  }

  /**
   * Handle a GET request when the requested format type was plain text.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   */
  protected void doGetText(HttpServletRequest req, HttpServletResponse res) throws IOException {
    res.sendError(SC_BAD_REQUEST);
  }

  /**
   * Handle a GET request when the requested format type was JSON.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   */
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    res.sendError(SC_BAD_REQUEST);
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
  private final GitilesAccess.Factory accessFactory;

  protected BaseServlet(Renderer renderer, GitilesAccess.Factory accessFactory) {
    this.renderer = renderer;
    this.accessFactory = accessFactory;
  }

  /**
   * Render data to HTML using Soy.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   * @param templateName Soy template name; must be in one of the template files
   *     defined in {@link Renderer}.
   * @param soyData data for Soy.
   * @throws IOException an error occurred during rendering.
   */
  protected void renderHtml(
      HttpServletRequest req, HttpServletResponse res, String templateName, Map<String, ?> soyData)
      throws IOException {
    renderer.render(req, res, templateName, startHtmlResponse(req, res, soyData));
  }

  /**
   * Start a streaming HTML response with header and footer rendered by Soy.
   * <p>
   * A streaming template includes the special template
   * {@code gitiles.streamingPlaceholder} at the point where data is to be
   * streamed. The template before and after this placeholder is rendered using
   * the provided data map.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   * @param templateName Soy template name; must be in one of the template files
   *     defined in {@link Renderer}.
   * @param soyData data for Soy.
   * @return output stream to render to. The portion of the template before the
   *     placeholder is already written and flushed; the portion after is
   *     written only on calling {@code close()}.
   * @throws IOException an error occurred during rendering the header.
   */
  protected OutputStream startRenderStreamingHtml(
      HttpServletRequest req, HttpServletResponse res, String templateName, Map<String, ?> soyData)
      throws IOException {
    req.setAttribute(STREAMING_ATTRIBUTE, true);
    return renderer.renderStreaming(res, templateName, startHtmlResponse(req, res, soyData));
  }

  private Map<String, ?> startHtmlResponse(
      HttpServletRequest req, HttpServletResponse res, Map<String, ?> soyData) throws IOException {
    res.setContentType(FormatType.HTML.getMimeType());
    res.setCharacterEncoding(UTF_8.name());
    setCacheHeaders(res);

    Map<String, Object> allData = getData(req);

    // for backwards compatibility, first try to access the old customHeader config var,
    // then read the new customVariant variable.
    GitilesConfig.putVariant(getAccess(req).getConfig(), "customHeader", "customVariant", allData);
    GitilesConfig.putVariant(getAccess(req).getConfig(), "customVariant", "customVariant", allData);
    allData.putAll(soyData);
    GitilesView view = ViewFilter.getView(req);
    if (!allData.containsKey("repositoryName") && view.getRepositoryName() != null) {
      allData.put("repositoryName", view.getRepositoryName());
    }
    if (!allData.containsKey("breadcrumbs")) {
      allData.put("breadcrumbs", view.getBreadcrumbs());
    }

    res.setStatus(HttpServletResponse.SC_OK);
    return allData;
  }

  /**
   * Render data to JSON using GSON.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   * @param src @see com.google.gson.Gson#toJson(Object, Type, Appendable)
   * @param typeOfSrc @see com.google.gson.Gson#toJson(Object, Type, Appendable)
   */
  protected void renderJson(
      HttpServletRequest req, HttpServletResponse res, Object src, Type typeOfSrc)
      throws IOException {
    setApiHeaders(res, JSON);
    res.setStatus(SC_OK);
    try (Writer writer = newWriter(req, res)) {
      newGsonBuilder(req).create().toJson(src, typeOfSrc, writer);
      writer.write('\n');
    }
  }

  @SuppressWarnings("unused") // Used in subclasses.
  protected GsonBuilder newGsonBuilder(HttpServletRequest req) throws IOException {
    return new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setPrettyPrinting()
        .generateNonExecutableJson();
  }

  /**
   * @see #startRenderText(HttpServletRequest, HttpServletResponse)
   * @param req in-progress request.
   * @param res in-progress response.
   * @param contentType contentType to set.
   * @return the response's writer.
   */
  protected Writer startRenderText(
      HttpServletRequest req, HttpServletResponse res, String contentType) throws IOException {
    setApiHeaders(res, contentType);
    return newWriter(req, res);
  }

  /**
   * Prepare the response to render plain text.
   * <p>
   * Unlike
   * {@link #renderHtml(HttpServletRequest, HttpServletResponse, String, Map)}
   * and
   * {@link #renderJson(HttpServletRequest, HttpServletResponse, Object, Type)},
   * which assume the data to render is already completely prepared, this method
   * does not write any data, only headers, and returns the response's
   * ready-to-use writer.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   * @return the response's writer.
   */
  protected Writer startRenderText(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    return startRenderText(req, res, TEXT.getMimeType());
  }

  /**
   * Render an error as plain text.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   * @param statusCode HTTP status code.
   * @param message full message text.
   *
   * @throws IOException
   */
  protected void renderTextError(
      HttpServletRequest req, HttpServletResponse res, int statusCode, String message)
      throws IOException {
    res.setStatus(statusCode);
    setApiHeaders(res, TEXT);
    setCacheHeaders(res);
    try (Writer out = newWriter(req, res)) {
      out.write(message);
    }
  }

  protected GitilesAccess getAccess(HttpServletRequest req) {
    GitilesAccess access = (GitilesAccess) req.getAttribute(ACCESS_ATTRIBUTE);
    if (access == null) {
      access = accessFactory.forRequest(req);
      req.setAttribute(ACCESS_ATTRIBUTE, access);
    }
    return access;
  }

  protected void setCacheHeaders(HttpServletResponse res) {
    setNotCacheable(res);
  }

  protected void setApiHeaders(HttpServletResponse res, String contentType) {
    if (!Strings.isNullOrEmpty(contentType)) {
      res.setContentType(contentType);
    }
    res.setCharacterEncoding(UTF_8.name());
    res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment");
    res.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    setCacheHeaders(res);
  }

  protected void setApiHeaders(HttpServletResponse res, FormatType type) {
    setApiHeaders(res, type.getMimeType());
  }

  protected void setDownloadHeaders(HttpServletResponse res, String filename, String contentType) {
    res.setContentType(contentType);
    res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
    setCacheHeaders(res);
  }

  protected static Writer newWriter(OutputStream os, HttpServletResponse res) throws IOException {
    return new OutputStreamWriter(os, res.getCharacterEncoding());
  }

  private Writer newWriter(HttpServletRequest req, HttpServletResponse res) throws IOException {
    OutputStream out;
    if (acceptsGzipEncoding(req)) {
      res.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
      res.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
      out = new GZIPOutputStream(res.getOutputStream());
    } else {
      out = res.getOutputStream();
    }
    return newWriter(out, res);
  }

  protected static boolean acceptsGzipEncoding(HttpServletRequest req) {
    String accepts = req.getHeader(HttpHeaders.ACCEPT_ENCODING);
    if (accepts == null) {
      return false;
    }
    for (int b = 0; b < accepts.length(); ) {
      int comma = accepts.indexOf(',', b);
      int e = 0 <= comma ? comma : accepts.length();
      String term = accepts.substring(b, e).trim();
      if (term.equals(ENCODING_GZIP)) {
        return true;
      }
      b = e + 1;
    }
    return false;
  }

  protected static byte[] gzip(byte[] raw) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
      gz.write(raw);
    }
    return out.toByteArray();
  }
}
