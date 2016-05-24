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
import com.google.gitiles.doc.ImageLoader;
import com.google.gitiles.doc.MarkdownToHtml;
import com.google.template.soy.data.SanitizedContent;

import org.commonmark.node.Node;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class ReadmeHelper {
  private static final Logger log = LoggerFactory.getLogger(ReadmeHelper.class);

  private final ObjectReader reader;
  private final GitilesView view;
  private final Config cfg;
  private final RevTree rootTree;
  private final boolean render;

  private String readmePath;
  private ObjectId readmeId;

  ReadmeHelper(ObjectReader reader, GitilesView view, Config cfg, RevTree rootTree) {
    this.reader = reader;
    this.view = view;
    this.cfg = cfg;
    this.rootTree = rootTree;
    render = cfg.getBoolean("markdown", "render", true);
  }

  void scanTree(RevTree tree)
      throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
          IOException {
    if (render) {
      TreeWalk tw = new TreeWalk(reader);
      tw.setRecursive(false);
      tw.addTree(tree);
      while (tw.next() && !isPresent()) {
        considerEntry(tw);
      }
    }
  }

  void considerEntry(TreeWalk tw) {
    if (render
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
      int inputLimit = cfg.getInt("markdown", "inputLimit", 5 << 20);
      byte[] raw = reader.open(readmeId, Constants.OBJ_BLOB).getCachedBytes(inputLimit);
      String md = RawParseUtils.decode(raw);
      Node root = GitilesMarkdown.parse(md);
      if (root == null) {
        return null;
      }

      int imageLimit = cfg.getInt("markdown", "imageLimit", 256 << 10);
      ImageLoader img = null;
      if (imageLimit > 0) {
        img = new ImageLoader(reader, view, rootTree, readmePath, imageLimit);
      }

      return new MarkdownToHtml(view, cfg, readmePath).setImageLoader(img).toSoyHtml(root);
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
