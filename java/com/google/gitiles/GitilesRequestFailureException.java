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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Indicates the request should be failed.
 *
 * <p>When an HTTP request should be failed, throw this exception instead of directly setting an
 * HTTP status code. The exception is caught by an error handler in {@link GitilesFilter}. By
 * default, {@link DefaultErrorHandlingFilter} handles this exception and set an appropriate HTTP
 * status code. If you want to customize how the error is surfaced, like changing the error page
 * rendering, replace this error handler from {@link GitilesServlet}.
 *
 * <h2>Extending the error space</h2>
 *
 * <p>{@link GitilesServlet} lets you customize some parts of Gitiles, and sometimes you would like
 * to create a new {@link FailureReason}. For example, a customized {@code RepositoryResolver} might
 * check a request quota and reject a request if a client sends too many requests. In that case, you
 * can define your own {@link RuntimeException} and an error handler.
 *
 * <pre><code>
 *   public final class MyRequestFailureException extends RuntimeException {
 *     private final FailureReason reason;
 *
 *     public MyRequestFailureException(FailureReason reason) {
 *       super();
 *       this.reason = reason;
 *     }
 *
 *     public FailureReason getReason() {
 *       return reason;
 *     }
 *
 *     enum FailureReason {
 *       QUOTA_EXCEEDED(429);
 *     }
 *
 *     private final int httpStatusCode;
 *
 *     FailureReason(int httpStatusCode) {
 *       this.httpStatusCode = httpStatusCode;
 *     }
 *
 *     public int getHttpStatusCode() {
 *       return httpStatusCode;
 *     }
 *   }
 *
 *   public class MyErrorHandlingFilter extends AbstractHttpFilter {
 *     private static final DefaultErrorHandlingFilter delegate =
 *         new DefaultErrorHandlingFilter();
 *
 *     {@literal @}Override
 *     public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
 *         throws IOException, ServletException {
 *       try {
 *         delegate.doFilter(req, res, chain);
 *       } catch (MyRequestFailureException e) {
 *         res.setHeader(DefaultErrorHandlingFilter.GITILES_ERROR, e.getReason().toString());
 *         res.sendError(e.getReason().getHttpStatusCode());
 *       }
 *     }
 *   }
 * </code></pre>
 *
 * <p>{@code RepositoryResolver} can throw {@code MyRequestFailureException} and {@code
 * MyErrorHandlingFilter} will handle that. You can control how the error should be surfaced.
 */
public final class GitilesRequestFailureException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final FailureReason reason;
  private String publicErrorMessage;

  public GitilesRequestFailureException(FailureReason reason) {
    super();
    this.reason = reason;
  }

  public GitilesRequestFailureException(FailureReason reason, Throwable cause) {
    super(cause);
    this.reason = reason;
  }

  public GitilesRequestFailureException withPublicErrorMessage(String format, Object... params) {
    this.publicErrorMessage = String.format(format, params);
    return this;
  }

  public FailureReason getReason() {
    return reason;
  }

  @Nullable
  public String getPublicErrorMessage() {
    return Optional.ofNullable(publicErrorMessage).orElse(reason.getMessage());
  }

  /** The request failure reason. */
  public enum FailureReason {
    /** The object specified by the URL is ambiguous and Gitiles cannot identify one object. */
    AMBIGUOUS_OBJECT(
        SC_BAD_REQUEST,
        "The object specified by the URL is ambiguous and Gitiles cannot identify one object"),
    /** There's nothing to show for blame (e.g. the file is empty). */
    BLAME_REGION_NOT_FOUND(SC_NOT_FOUND, "There's nothing to show for blame"),
    /** Cannot parse URL as a Gitiles URL. */
    CANNOT_PARSE_GITILES_VIEW(SC_NOT_FOUND, "Cannot parse URL as a Gitiles URL"),
    /** URL parameters are not valid. */
    INCORECT_PARAMETER(SC_BAD_REQUEST, "URL parameters are not valid"),
    /**
     * The object specified by the URL is not suitable for the view (e.g. trying to show a blob as a
     * tree).
     */
    INCORRECT_OBJECT_TYPE(
        SC_BAD_REQUEST, "The object specified by the URL is not suitable for the view"),
    /** Markdown rendering is not enabled. */
    MARKDOWN_NOT_ENABLED(SC_NOT_FOUND, "Markdown rendering is not enabled"),
    /** Request is not authorized. */
    NOT_AUTHORIZED(SC_UNAUTHORIZED, "Request is not authorized"),
    /** Object is not found. */
    OBJECT_NOT_FOUND(SC_NOT_FOUND, "Object is not found"),
    /** Object is too large to show. */
    OBJECT_TOO_LARGE(SC_INTERNAL_SERVER_ERROR, "Object is too large to show"),
    /** Repository is not found. */
    REPOSITORY_NOT_FOUND(SC_NOT_FOUND, "Repository is not found"),
    /** Gitiles is not enabled for the repository. */
    SERVICE_NOT_ENABLED(SC_FORBIDDEN, "Gitiles is not enabled for the repository"),
    /** GitWeb URL cannot be converted to Gitiles URL. */
    UNSUPPORTED_GITWEB_URL(SC_GONE, "GitWeb URL cannot be converted to Gitiles URL"),
    /** The specified object's type is not supported. */
    UNSUPPORTED_OBJECT_TYPE(SC_NOT_FOUND, "The specified object's type is not supported"),
    /** The specified format type is not supported. */
    UNSUPPORTED_RESPONSE_FORMAT(SC_BAD_REQUEST, "The specified format type is not supported"),
    /** The specified revision names are not supported. */
    UNSUPPORTED_REVISION_NAMES(SC_BAD_REQUEST, "The specified revision names are not supported"),
    /** Internal server error. */
    INTERNAL_SERVER_ERROR(SC_INTERNAL_SERVER_ERROR, "Internal server error");

    private final int httpStatusCode;
    private final String message;

    FailureReason(int httpStatusCode, String message) {
      this.httpStatusCode = httpStatusCode;
      this.message = message;
    }

    public int getHttpStatusCode() {
      return httpStatusCode;
    }

    public String getMessage() {
      return message;
    }
  }
}
