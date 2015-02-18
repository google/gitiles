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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gitiles.PathServlet.FileType;
import com.google.gitiles.doc.GitilesMarkdown;
import com.google.gitiles.doc.ImageLoader;
import com.google.gitiles.doc.MarkdownToHtml;
import com.google.template.soy.data.SanitizedContent;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.pegdown.ast.RootNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Soy data converter for git trees. */
public class TreeSoyData {
  private static final Logger log = LoggerFactory.getLogger(TreeSoyData.class);

  /**
   * Number of characters to display for a symlink target. Targets longer than
   * this are abbreviated for display in a tree listing.
   */
  private static final int MAX_SYMLINK_TARGET_LENGTH = 72;

  /**
   * Maximum number of bytes to load from a blob that claims to be a symlink. If
   * the blob is larger than this byte limit it will be displayed as a binary
   * file instead of as a symlink.
   */
  static final int MAX_SYMLINK_SIZE = 16 << 10;

  static String resolveTargetUrl(GitilesView view, String target) {
    String resolved = PathUtil.simplifyPathUpToRoot(target, view.getPathPart());
    if (resolved == null) {
      return null;
    }
    return GitilesView.path()
        .copyFrom(view)
        .setPathPart(resolved)
        .toUrl();
  }

  @VisibleForTesting
  static String getTargetDisplayName(String target) {
    if (target.length() <= MAX_SYMLINK_TARGET_LENGTH) {
      return target;
    } else {
      int lastSlash = target.lastIndexOf('/');
      // TODO(dborowitz): Doesn't abbreviate a long last path component.
      return lastSlash >= 0 ? "..." + target.substring(lastSlash) : target;
    }
  }

  private final ObjectReader reader;
  private final GitilesView view;
  private final Config cfg;
  private final RevTree rootTree;
  private ArchiveFormat archiveFormat;

  public TreeSoyData(ObjectReader reader, GitilesView view, Config cfg,
      RevTree rootTree) {
    this.reader = reader;
    this.view = view;
    this.cfg = cfg;
    this.rootTree = rootTree;
  }

  public TreeSoyData setArchiveFormat(ArchiveFormat archiveFormat) {
    this.archiveFormat = archiveFormat;
    return this;
  }

  public Map<String, Object> toSoyData(ObjectId treeId, TreeWalk tw) throws MissingObjectException,
         IOException {
    String readmePath = null;
    ObjectId readmeId = null;

    List<Object> entries = Lists.newArrayList();
    GitilesView.Builder urlBuilder = GitilesView.path().copyFrom(view);
    while (tw.next()) {
      FileType type = FileType.forEntry(tw);
      String name = tw.getNameString();

      switch (view.getType()) {
        case PATH:
          urlBuilder.setPathPart(view.getPathPart() + "/" + name);
          break;
        case REVISION:
          // Got here from a tag pointing at a tree.
          urlBuilder.setPathPart(name);
          break;
        default:
          throw new IllegalStateException(String.format(
              "Cannot render TreeSoyData from %s view", view.getType()));
      }

      String url = urlBuilder.toUrl();
      if (type == FileType.TREE) {
        name += "/";
        url += "/";
      }
      Map<String, String> entry = Maps.newHashMapWithExpectedSize(4);
      entry.put("type", type.toString());
      entry.put("name", name);
      entry.put("url", url);
      if (type == FileType.SYMLINK) {
        String target = new String(
            reader.open(tw.getObjectId(0)).getCachedBytes(),
            UTF_8);
        entry.put("targetName", getTargetDisplayName(target));
        String targetUrl = resolveTargetUrl(view, target);
        if (targetUrl != null) {
          entry.put("targetUrl", targetUrl);
        }
      } else if (isReadmeFile(name) && type == FileType.REGULAR_FILE) {
        readmePath = tw.getPathString();
        readmeId = tw.getObjectId(0);
      }
      entries.add(entry);
    }

    Map<String, Object> data = Maps.newHashMapWithExpectedSize(3);
    data.put("sha", treeId.name());
    data.put("entries", entries);

    if (view.getType() == GitilesView.Type.PATH
        && view.getRevision().getPeeledType() == OBJ_COMMIT) {
      data.put("logUrl", GitilesView.log().copyFrom(view).toUrl());
      data.put("archiveUrl", GitilesView.archive()
          .copyFrom(view)
          .setPathPart(Strings.emptyToNull(view.getPathPart()))
          .setExtension(archiveFormat.getDefaultSuffix())
          .toUrl());
      data.put("archiveType", archiveFormat.getShortName());
    }

    if (readmeId != null && cfg.getBoolean("markdown", "render", true)) {
      data.put("readmePath", readmePath);
      data.put("readmeHtml", render(readmePath, readmeId));
    }

    return data;
  }

  /** True if the file is the default markdown file to render in tree view. */
  private static boolean isReadmeFile(String name) {
    return name.equalsIgnoreCase("README.md");
  }

  public Map<String, Object> toSoyData(ObjectId treeId) throws MissingObjectException, IOException {
    TreeWalk tw = new TreeWalk(reader);
    tw.addTree(treeId);
    tw.setRecursive(false);
    return toSoyData(treeId, tw);
  }

  private SanitizedContent render(String path, ObjectId id) {
    try {
      int inputLimit = cfg.getInt("markdown", "inputLimit", 5 << 20);
      byte[] raw = reader.open(id, Constants.OBJ_BLOB).getCachedBytes(inputLimit);
      String md = RawParseUtils.decode(raw);
      RootNode root = GitilesMarkdown.parseFile(view, path, md);
      if (root == null) {
        return null;
      }

      int imageLimit = cfg.getInt("markdown", "imageLimit", 256 << 10);
      ImageLoader img = null;
      if (imageLimit > 0) {
        img = new ImageLoader(reader, view, rootTree, path, imageLimit);
      }

      return new MarkdownToHtml(view, cfg)
        .setImageLoader(img)
        .toSoyHtml(root);
    } catch (LargeObjectException | IOException e) {
      log.error(String.format("error rendering %s/%s/%s",
          view.getRepositoryName(), view.getPathPart(), path), e);
      return null;
    }
  }
}
