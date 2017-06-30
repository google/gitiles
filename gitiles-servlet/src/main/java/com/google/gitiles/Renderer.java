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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.template.soy.tofu.SoyTofu;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Renderer for Soy templates used by Gitiles.
 *
 * <p>Most callers should not use the methods in this class directly, and instead use one of the
 * HTML methods in {@link BaseServlet}.
 */
public abstract class Renderer {
  // Must match .streamingPlaceholder.
  private static final String PLACEHOLDER = "id=\"STREAMED_OUTPUT_BLOCK\"";

  private static final ImmutableList<String> SOY_FILENAMES =
      ImmutableList.of(
          "BlameDetail.soy",
          "Common.soy",
          "DiffDetail.soy",
          "Doc.soy",
          "HostIndex.soy",
          "LogDetail.soy",
          "ObjectDetail.soy",
          "PathDetail.soy",
          "RefList.soy",
          "RevisionDetail.soy",
          "RepositoryIndex.soy");

  public static final ImmutableMap<String, String> STATIC_URL_GLOBALS =
      ImmutableMap.of(
          "gitiles.BASE_CSS_URL", "base.css",
          "gitiles.DOC_CSS_URL", "doc.css",
          "gitiles.PRETTIFY_CSS_URL", "prettify/prettify.css");

  protected static Function<String, URL> fileUrlMapper() {
    return fileUrlMapper("");
  }

  protected static Function<String, URL> fileUrlMapper(String prefix) {
    checkNotNull(prefix);
    return filename -> {
      if (filename == null) {
        return null;
      }
      try {
        return new File(prefix + filename).toURI().toURL();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(e);
      }
    };
  }

  protected ImmutableMap<String, URL> templates;
  protected ImmutableMap<String, String> globals;
  private final ConcurrentMap<String, HashCode> hashes =
      new MapMaker().initialCapacity(SOY_FILENAMES.size()).concurrencyLevel(1).makeMap();

  protected Renderer(
      Function<String, URL> resourceMapper,
      Map<String, String> globals,
      String staticPrefix,
      Iterable<URL> customTemplates,
      String siteTitle) {
    checkNotNull(staticPrefix, "staticPrefix");

    ImmutableMap.Builder<String, URL> b = ImmutableMap.builder();
    for (String name : SOY_FILENAMES) {
      b.put(name, resourceMapper.apply(name));
    }
    for (URL u : customTemplates) {
      b.put(u.toString(), u);
    }
    templates = b.build();

    Map<String, String> allGlobals = Maps.newHashMap();
    for (Map.Entry<String, String> e : STATIC_URL_GLOBALS.entrySet()) {
      allGlobals.put(e.getKey(), staticPrefix + e.getValue());
    }
    allGlobals.put("gitiles.SITE_TITLE", siteTitle);
    allGlobals.putAll(globals);
    this.globals = ImmutableMap.copyOf(allGlobals);
  }

  public HashCode getTemplateHash(String soyFile) {
    HashCode h = hashes.get(soyFile);
    if (h == null) {
      h = computeTemplateHash(soyFile);
      hashes.put(soyFile, h);
    }
    return h;
  }

  HashCode computeTemplateHash(String soyFile) {
    URL u = templates.get(soyFile);
    checkState(u != null, "Missing Soy template %s", soyFile);

    Hasher h = Hashing.murmur3_128().newHasher();
    try (InputStream is = u.openStream();
        OutputStream os = Funnels.asOutputStream(h)) {
      ByteStreams.copy(is, os);
    } catch (IOException e) {
      throw new IllegalStateException("Missing Soy template " + soyFile, e);
    }
    return h.hash();
  }

  public String render(String templateName, Map<String, ?> soyData) {
    return newRenderer(templateName).setData(soyData).render();
  }

  void render(
      HttpServletRequest req, HttpServletResponse res, String templateName, Map<String, ?> soyData)
      throws IOException {
    res.setContentType("text/html");
    res.setCharacterEncoding("UTF-8");
    byte[] data = newRenderer(templateName).setData(soyData).render().getBytes(UTF_8);
    if (BaseServlet.acceptsGzipEncoding(req)) {
      res.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
      res.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
      data = BaseServlet.gzip(data);
    }
    res.setContentLength(data.length);
    res.getOutputStream().write(data);
  }

  OutputStream renderStreaming(HttpServletResponse res, String templateName, Map<String, ?> soyData)
      throws IOException {
    return renderStreaming(res, false, templateName, soyData);
  }

  OutputStream renderStreaming(
      HttpServletResponse res, boolean gzip, String templateName, Map<String, ?> soyData)
      throws IOException {
    String html = newRenderer(templateName).setData(soyData).render();
    int id = html.indexOf(PLACEHOLDER);
    checkArgument(id >= 0, "Template must contain %s", PLACEHOLDER);

    int lt = html.lastIndexOf('<', id);
    int gt = html.indexOf('>', id + PLACEHOLDER.length());

    OutputStream out = gzip ? new GZIPOutputStream(res.getOutputStream()) : res.getOutputStream();
    out.write(html.substring(0, lt).getBytes(UTF_8));
    out.flush();

    byte[] tail = html.substring(gt + 1).getBytes(UTF_8);
    return new OutputStream() {
      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
      }

      @Override
      public void write(int b) throws IOException {
        out.write(b);
      }

      @Override
      public void flush() throws IOException {
        out.flush();
      }

      @Override
      public void close() throws IOException {
        try (OutputStream o = out) {
          o.write(tail);
        }
      }
    };
  }

  SoyTofu.Renderer newRenderer(String templateName) {
    return getTofu().newRenderer(templateName);
  }

  protected abstract SoyTofu getTofu();
}
