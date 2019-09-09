// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.gitiles;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.gitiles.GitilesRequestFailureException.FailureReason;
import java.io.IOException;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Convert exceptions into HTTP response. */
public class DefaultErrorHandlingFilter extends AbstractHttpFilter {
  private static final Logger log = LoggerFactory.getLogger(DefaultErrorHandlingFilter.class);

  /** HTTP header that indicates an error detail. */
  public static final String GITILES_ERROR = "X-Gitiles-Error";

  private Renderer renderer;

  public DefaultErrorHandlingFilter(Renderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(req, res);
    } catch (GitilesRequestFailureException e) {
      try {
        res.setHeader(GITILES_ERROR, e.getReason().toString());
        renderHtml(req, res, e.getReason().getHttpStatusCode(), e.getPublicErrorMessage());
      } catch (IOException e2) {
        e.addSuppressed(e2);
        throw e;
      }
    } catch (RepositoryNotFoundException e) {
      try {
        renderHtml(req, res, FailureReason.REPOSITORY_NOT_FOUND);
      } catch (IOException e2) {
        e.addSuppressed(e2);
        throw e;
      }
    } catch (AmbiguousObjectException e) {
      try {
        renderHtml(req, res, FailureReason.AMBIGUOUS_OBJECT);
      } catch (IOException e2) {
        e.addSuppressed(e2);
        throw e;
      }
    } catch (ServiceMayNotContinueException e) {
      try {
        renderHtml(req, res, e.getStatusCode(), e.getMessage());
      } catch (IOException e2) {
        e.addSuppressed(e2);
        throw e;
      }
    } catch (IOException | ServletException e) {
      try {
        log.warn("Internal server error", e);
        renderHtml(req, res, FailureReason.INTERNAL_SERVER_ERROR);
      } catch (IOException e2) {
        e.addSuppressed(e2);
        throw e;
      }
    }
  }

  private void renderHtml(HttpServletRequest req, HttpServletResponse res, FailureReason reason)
      throws IOException {
    res.setHeader(GITILES_ERROR, reason.toString());
    renderHtml(req, res, reason.getHttpStatusCode(), reason.getMessage());
  }

  private void renderHtml(
      HttpServletRequest req, HttpServletResponse res, int status, String message)
      throws IOException {
    res.setStatus(status);
    renderHtml(req, res, "gitiles.error", ImmutableMap.of("title", message));
  }

  protected void renderHtml(
      HttpServletRequest req, HttpServletResponse res, String templateName, Map<String, ?> soyData)
      throws IOException {
    renderer.renderHtml(req, res, templateName, startHtmlResponse(req, res, soyData));
  }

  private Map<String, ?> startHtmlResponse(
      HttpServletRequest req, HttpServletResponse res, Map<String, ?> soyData) {
    res.setContentType(FormatType.HTML.getMimeType());
    res.setCharacterEncoding(UTF_8.name());
    BaseServlet.setNotCacheable(res);
    Map<String, Object> allData = BaseServlet.getData(req);
    allData.putAll(soyData);
    return allData;
  }
}
