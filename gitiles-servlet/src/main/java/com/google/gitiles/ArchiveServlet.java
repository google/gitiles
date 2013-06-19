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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableMap;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.archive.TarFormat;
import org.eclipse.jgit.archive.Tbz2Format;
import org.eclipse.jgit.archive.TgzFormat;
import org.eclipse.jgit.archive.TxzFormat;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ArchiveServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  private enum Format {
    TAR("application/x-tar", new TarFormat()),
    TGZ("application/x-gzip", new TgzFormat()),
    TBZ2("application/x-bzip2", new Tbz2Format()),
    TXZ("application/x-xz", new TxzFormat());
    // Zip is not supported because it may be interpreted by a Java plugin as a
    // valid JAR file, whose code would have access to cookies on the domain.

    private final ArchiveCommand.Format<?> format;
    private final String mimeType;

    private Format(String mimeType, ArchiveCommand.Format<?> format) {
      this.format = format;
      this.mimeType = mimeType;
      ArchiveCommand.registerFormat(name(), format);
    }
  }

  private static final Map<String, Format> FORMATS_BY_EXTENSION;

  static {
    ImmutableMap.Builder<String, Format> exts = ImmutableMap.builder();
    for (Format format : Format.values()) {
      for (String ext : format.format.suffixes()) {
        exts.put(ext, format);
      }
    }
    FORMATS_BY_EXTENSION = exts.build();
  }

  static Set<String> validExtensions() {
    return FORMATS_BY_EXTENSION.keySet();
  }

  public ArchiveServlet() {
    super(null);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    GitilesView view = ViewFilter.getView(req);
    Revision rev = view.getRevision();
    Repository repo = ServletUtils.getRepository(req);

    // Check object type before starting the archive. If we just caught the
    // exception from cmd.call() below, we wouldn't know whether it was because
    // the input object is not a tree or something broke later.
    RevWalk walk = new RevWalk(repo);
    try {
      walk.parseTree(rev.getId());
    } catch (IncorrectObjectTypeException e) {
      res.sendError(SC_NOT_FOUND);
      return;
    } finally {
      walk.release();
    }

    Format format = FORMATS_BY_EXTENSION.get(view.getExtension());
    String filename = getFilename(view, rev, view.getExtension());
    setDownloadHeaders(req, res, filename, format.mimeType);
    res.setStatus(SC_OK);

    try {
      new ArchiveCommand(repo)
          .setFormat(format.name())
          .setTree(rev.getId())
          .setOutputStream(res.getOutputStream())
          .call();
    } catch (GitAPIException e) {
      throw new IOException(e);
    }
  }

  private String getFilename(GitilesView view, Revision rev, String ext) {
    return new StringBuilder()
        .append(Paths.basename(view.getRepositoryName()))
        .append('-')
        .append(rev.getName())
        .append(ext)
        .toString();
  }
}
