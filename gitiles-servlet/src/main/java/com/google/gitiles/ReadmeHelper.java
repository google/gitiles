// Copyright 2015 Google Inc. All Rights Reserved.
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
import com.google.gitiles.doc.GitilesMarkdown;
import com.google.gitiles.doc.MarkdownConfig;
import com.google.gitiles.doc.MarkdownToHtml;
import com.google.template.soy.data.SanitizedContent;
import java.io.IOException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ReadmeHelper {
  private static final Logger log = LoggerFactory.getLogger(ReadmeHelper.class);

  private final ObjectReader reader;
  private final GitilesView view;
  private final MarkdownConfig config;
  private final RevTree rootTree;
  private final String requestUri;

  private String readmePath;
  private ObjectId readmeId;

  ReadmeHelper(
      ObjectReader reader,
      GitilesView view,
      MarkdownConfig config,
      RevTree rootTree,
      String requestUri) {
    this.reader = reader;
    this.view = view;
    this.config = config;
    this.rootTree = rootTree;
    this.requestUri = requestUri;
  }

  void scanTree(RevTree tree)
      throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
          IOException {
    if (config.render) {
      TreeWalk tw = new TreeWalk(reader);
      tw.setRecursive(false);
      tw.addTree(tree);
      while (tw.next() && !isPresent()) {
        considerEntry(tw);
      }
    }
  }

  void considerEntry(TreeWalk tw) {
    if (config.render
        && FileMode.REGULAR_FILE.equals(tw.getRawMode(0))
        && isReadmeFile(tw.getNameString())) {
      readmePath = tw.getPathString();
      readmeId = tw.getObjectId(0);
    }
  }

  boolean isPresent() {
    return readmeId != null;
  }

  String getPath() {
    return readmePath;
  }

  SanitizedContent render() {
    try {
      byte[] raw = reader.open(readmeId, Constants.OBJ_BLOB).getCachedBytes(config.inputLimit);
      return MarkdownToHtml.builder()
          .setConfig(config)
          .setGitilesView(view)
          .setRequestUri(requestUri)
          .setFilePath(readmePath)
          .setReader(reader)
          .setRootTree(rootTree)
          .build()
          .toSoyHtml(GitilesMarkdown.parse(raw));
    } catch (RuntimeException | IOException err) {
      log.error(
          String.format(
              "error rendering %s/%s %s:%s",
              view.getHostName(),
              view.getRepositoryName(),
              view.getRevision(),
              readmePath),
          err);
      return null;
    }
  }

  /** True if the file is the default markdown file to render in tree view. */
  private static boolean isReadmeFile(String name) {
    return name.equalsIgnoreCase("README.md");
  }
}
