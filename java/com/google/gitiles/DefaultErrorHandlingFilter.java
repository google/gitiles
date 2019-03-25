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
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.sendError;

import java.io.IOException;
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

  @Override
  public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(req, res);
    } catch (GitilesRequestFailureException e) {
      res.setHeader(GITILES_ERROR, e.getReason().toString());
      String publicMessage = e.getPublicErrorMessage();
      if (publicMessage != null) {
        res.sendError(e.getReason().getHttpStatusCode(), publicMessage);
      } else {
        res.sendError(e.getReason().getHttpStatusCode());
      }
    } catch (RepositoryNotFoundException e) {
      res.sendError(SC_NOT_FOUND);
    } catch (AmbiguousObjectException e) {
      res.sendError(SC_BAD_REQUEST);
    } catch (ServiceMayNotContinueException e) {
      sendError(req, res, e.getStatusCode(), e.getMessage());
    } catch (IOException | ServletException err) {
      log.warn("Internal server error", err);
      res.sendError(SC_INTERNAL_SERVER_ERROR);
    }
  }
}
