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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.gitiles.GitilesView;
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

/** Reads an image from Git and converts to {@code data:image/*;base64,...} */
public class ImageLoader {
  private static final Logger log = LoggerFactory.getLogger(ImageLoader.class);

  private final ObjectReader reader;
  private final GitilesView view;
  private final RevTree root;
  private final String path;
  private final int imageLimit;

  public ImageLoader(
      ObjectReader reader, GitilesView view, RevTree root, String path, int maxImageSize) {
    this.reader = reader;
    this.view = view;
    this.root = root;
    this.path = path;
    this.imageLimit = maxImageSize;
  }

  String loadImage(String src) {
    if (src.startsWith("/")) {
      return readAndBase64Encode(src.substring(1));
    }

    String base = directory();
    while (src.startsWith("../")) {
      int s = base.lastIndexOf('/');
      if (s == -1) {
        return FilterImageDataUri.INSTANCE.getInnocuousOutput();
      }
      base = base.substring(0, s + 1);
      src = src.substring("../".length());
    }
    return readAndBase64Encode(base + src);
  }

  private String directory() {
    int s = path.lastIndexOf('/');
    if (s > 0) {
      return path.substring(0, s + 1);
    }
    return "";
  }

  private String readAndBase64Encode(String path) {
    String type = getMimeType(path);
    if (type == null) {
      return FilterImageDataUri.INSTANCE.getInnocuousOutput();
    }

    try {
      TreeWalk tw = TreeWalk.forPath(reader, path, root);
      if (tw == null || tw.getFileMode(0) != FileMode.REGULAR_FILE) {
        return FilterImageDataUri.INSTANCE.getInnocuousOutput();
      }

      ObjectId id = tw.getObjectId(0);
      byte[] raw = reader.open(id, Constants.OBJ_BLOB).getCachedBytes(imageLimit);
      if (raw.length > imageLimit) {
        return FilterImageDataUri.INSTANCE.getInnocuousOutput();
      }

      return "data:" + type + ";base64," + BaseEncoding.base64().encode(raw);
    } catch (LargeObjectException.ExceedsLimit e) {
      return FilterImageDataUri.INSTANCE.getInnocuousOutput();
    } catch (IOException e) {
      log.error(
          String.format(
              "cannot read repo %s image %s from %s", view.getRepositoryName(), path, root.name()),
          e);
      return FilterImageDataUri.INSTANCE.getInnocuousOutput();
    }
  }

  private static final ImmutableMap<String, String> TYPES =
      ImmutableMap.of(
          "png", "image/png",
          "gif", "image/gif",
          "jpg", "image/jpeg",
          "jpeg", "image/jpeg");

  private static String getMimeType(String path) {
    int d = path.lastIndexOf('.');
    if (d == -1) {
      return null;
    }
    String ext = path.substring(d + 1);
    return TYPES.get(ext.toLowerCase());
  }
}
