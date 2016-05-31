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

package com.google.gitiles.doc;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.gitiles.GitilesView;
import com.google.gitiles.MimeTypes;
import com.google.template.soy.shared.restricted.EscapingConventions.FilterImageDataUri;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.annotation.Nullable;

/** Reads an image from Git and converts to {@code data:image/*;base64,...} */
class ImageLoader {
  private static final Logger log = LoggerFactory.getLogger(ImageLoader.class);
  private static final ImmutableSet<String> ALLOWED_TYPES =
      ImmutableSet.of("image/gif", "image/jpeg", "image/png");

  private final ObjectReader reader;
  private final GitilesView view;
  private final MarkdownConfig config;
  private final RevTree root;

  ImageLoader(ObjectReader reader, GitilesView view, MarkdownConfig config, RevTree root) {
    this.reader = reader;
    this.view = view;
    this.config = config;
    this.root = root;
  }

  String inline(@Nullable String markdownPath, String imagePath) {
    String data = inlineMaybe(markdownPath, imagePath);
    if (data != null) {
      return data;
    }
    return FilterImageDataUri.INSTANCE.getInnocuousOutput();
  }

  private String inlineMaybe(@Nullable String markdownPath, String imagePath) {
    if (config.imageLimit <= 0) {
      return null;
    }

    String path = PathResolver.resolve(markdownPath, imagePath);
    if (path == null) {
      return null;
    }

    String type = MimeTypes.getMimeType(path);
    if (!ALLOWED_TYPES.contains(type)) {
      return null;
    }

    try {
      TreeWalk tw = TreeWalk.forPath(reader, path, root);
      if (tw == null || tw.getFileMode(0) != FileMode.REGULAR_FILE) {
        return null;
      }

      ObjectId id = tw.getObjectId(0);
      byte[] raw = reader.open(id, Constants.OBJ_BLOB).getCachedBytes(config.imageLimit);
      if (raw.length > config.imageLimit) {
        return null;
      }
      return "data:" + type + ";base64," + BaseEncoding.base64().encode(raw);
    } catch (LargeObjectException.ExceedsLimit e) {
      return null;
    } catch (IOException err) {
      String repo = view != null ? view.getRepositoryName() : "<unknown>";
      log.error(
          String.format("cannot read repo %s image %s from %s", repo, path, root.name()), err);
      return null;
    }
  }
}
