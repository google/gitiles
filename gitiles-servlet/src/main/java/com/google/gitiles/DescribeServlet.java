// Copyright 2013 Google Inc. All Rights Reserved.
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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.NameRevCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves an API result describing an object. */
public class DescribeServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  private static final String ALL_PARAM = "all";
  private static final String CONTAINS_PARAM = "contains";
  private static final String TAGS_PARAM = "tags";

  private static boolean getBooleanParam(GitilesView view, String name) {
    List<String> values = view.getParameters().get(name);
    return !values.isEmpty() && (values.get(0).isEmpty() || values.get(0).equals("1"));
  }

  protected DescribeServlet(GitilesAccess.Factory accessFactory) {
    super(null, accessFactory);
  }

  @Override
  protected void doGetText(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String name = describe(ServletUtils.getRepository(req), ViewFilter.getView(req), req, res);
    if (name == null) {
      return;
    }
    try (Writer out = startRenderText(req, res)) {
      out.write(RefServlet.sanitizeRefForText(name));
    }
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String name = describe(ServletUtils.getRepository(req), ViewFilter.getView(req), req, res);
    if (name == null) {
      return;
    }
    renderJson(
        req,
        res,
        ImmutableMap.of(ViewFilter.getView(req).getPathPart(), name),
        new TypeToken<Map<String, String>>() {}.getType());
  }

  private ObjectId resolve(
      Repository repo, GitilesView view, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    String rev = view.getPathPart();
    try {
      return repo.resolve(rev);
    } catch (RevisionSyntaxException e) {
      renderTextError(
          req,
          res,
          SC_BAD_REQUEST,
          "Invalid revision syntax: " + RefServlet.sanitizeRefForText(rev));
      return null;
    } catch (AmbiguousObjectException e) {
      renderTextError(
          req,
          res,
          SC_BAD_REQUEST,
          String.format(
              "Ambiguous short SHA-1 %s (%s)",
              e.getAbbreviatedObjectId(),
              Joiner.on(", ").join(e.getCandidates())));
      return null;
    }
  }

  private String describe(
      Repository repo, GitilesView view, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (!getBooleanParam(view, CONTAINS_PARAM)) {
      res.setStatus(SC_BAD_REQUEST);
      return null;
    }
    ObjectId id = resolve(repo, view, req, res);
    if (id == null) {
      return null;
    }
    String name;
    try (Git git = new Git(repo)) {
      NameRevCommand cmd = nameRevCommand(git, id, req, res);
      if (cmd == null) {
        return null;
      }
      name = cmd.call().get(id);
    } catch (GitAPIException e) {
      throw new IOException(e);
    }
    if (name == null) {
      res.setStatus(SC_NOT_FOUND);
      return null;
    }
    return name;
  }

  private NameRevCommand nameRevCommand(
      Git git, ObjectId id, HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    NameRevCommand cmd = git.nameRev();
    boolean all = getBooleanParam(view, ALL_PARAM);
    boolean tags = getBooleanParam(view, TAGS_PARAM);
    if (all && tags) {
      renderTextError(req, res, SC_BAD_REQUEST, "Cannot specify both \"all\" and \"tags\"");
      return null;
    }
    if (all) {
      cmd.addPrefix(Constants.R_REFS);
    } else if (tags) {
      cmd.addPrefix(Constants.R_TAGS);
    } else {
      cmd.addAnnotatedTags();
    }
    cmd.add(id);
    return cmd;
  }
}
