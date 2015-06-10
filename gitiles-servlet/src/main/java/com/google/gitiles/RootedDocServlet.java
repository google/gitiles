// Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.gitiles;

import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_REPOSITORY;

import com.google.gitiles.doc.DocServlet;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves Markdown at the root of a host. */
public class RootedDocServlet extends HttpServlet {
  private static final Logger log = LoggerFactory.getLogger(ViewFilter.class);
  private static final long serialVersionUID = 1L;
  public static final String BRANCH = "refs/heads/md-pages";

  private final RepositoryResolver<HttpServletRequest> resolver;
  private final DocServlet docServlet;

  public RootedDocServlet(RepositoryResolver<HttpServletRequest> resolver,
      GitilesAccess.Factory accessFactory, Renderer renderer) {
    this.resolver = resolver;
    docServlet = new DocServlet(accessFactory, renderer);
  }

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    docServlet.init(config);
  }

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    try (Repository repo = resolver.open(req, null);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId id = repo.resolve(BRANCH);
      if (id == null) {
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      RevObject obj = rw.peel(rw.parseAny(id));
      if (!(obj instanceof RevCommit)) {
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      req.setAttribute(ATTRIBUTE_REPOSITORY, repo);
      ViewFilter.setView(req, GitilesView.rootedDoc()
          .setHostName(req.getServerName())
          .setServletPath(req.getContextPath() + req.getServletPath())
          .setRevision(BRANCH, obj)
          .setPathPart(req.getPathInfo())
          .build());

      docServlet.service(req, res);
    } catch (RepositoryNotFoundException | ServiceNotAuthorizedException
        | ServiceNotEnabledException e) {
      log.error(String.format("cannot open repository for %s", req.getServerName()), e);
      res.sendError(HttpServletResponse.SC_NOT_FOUND);
    } finally {
      ViewFilter.removeView(req);
      req.removeAttribute(ATTRIBUTE_REPOSITORY);
    }
  }
}
