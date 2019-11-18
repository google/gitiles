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

import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import prettify.parser.Prettify;
import syntaxhighlight.ParseResult;

/** Soy data converter for git blobs. */
public class BlobSoyData {
  private static final Logger log = LoggerFactory.getLogger(BlobSoyData.class);

  /**
   * Maximum number of bytes to load from a supposed text file for display. Files larger than this
   * will be displayed as binary files, even if the contents was text. For example really big XML
   * files may be above this limit and will get displayed as binary.
   */
  @VisibleForTesting static final int MAX_FILE_SIZE = 10 << 20;

  private final GitilesView view;
  private final ObjectReader reader;

  public BlobSoyData(ObjectReader reader, GitilesView view) {
    this.reader = reader;
    this.view = view;
  }

  public Map<String, Object> toSoyData(ObjectId blobId) throws MissingObjectException, IOException {
    return toSoyData(null, blobId);
  }

  public Map<String, Object> toSoyData(String path, ObjectId blobId)
      throws MissingObjectException, IOException {
    Map<String, Object> data = Maps.newHashMapWithExpectedSize(4);
    data.put("sha", ObjectId.toString(blobId));

    ObjectLoader loader = reader.open(blobId, Constants.OBJ_BLOB);
    String content;
    try {
      byte[] raw = loader.getCachedBytes(MAX_FILE_SIZE);
      content =
          (raw.length < MAX_FILE_SIZE && !RawText.isBinary(raw)) ? RawParseUtils.decode(raw) : null;
    } catch (LargeObjectException.OutOfMemory e) {
      throw e;
    } catch (LargeObjectException e) {
      content = null;
    }

    if (content != null) {
      data.put("lines", prettify(path, content));
      if (path != null && path.endsWith(".md")) {
        data.put("docUrl", GitilesView.doc().copyFrom(view).toUrl());
      }
    } else {
      data.put("lines", null);
      data.put("size", Long.toString(loader.getSize()));
    }
    if (path != null && view.getRevision().getPeeledType() == OBJ_COMMIT) {
      data.put("fileUrl", GitilesView.path().copyFrom(view).toUrl());
      data.put("logUrl", GitilesView.log().copyFrom(view).toUrl());
      data.put("blameUrl", GitilesView.blame().copyFrom(view).toUrl());
    }
    return data;
  }

  private SoyListData prettify(String path, String content) {
    List<ParseResult> results = parse(path, content);
    SoyListData lines = new SoyListData();
    SoyListData line = new SoyListData();
    lines.add(line);

    int last = 0;
    for (ParseResult r : results) {
      checkState(
          r.getOffset() >= last,
          "out-of-order ParseResult, expected %s >= %s",
          r.getOffset(),
          last);
      writeResult(lines, null, content, last, r.getOffset());
      last = r.getOffset() + r.getLength();
      writeResult(lines, r.getStyleKeysString(), content, r.getOffset(), last);
    }
    if (last < content.length()) {
      writeResult(lines, null, content, last, content.length());
    }
    return lines;
  }

  private List<ParseResult> parse(String path, String content) {
    String lang = extension(path, content);
    try {
      return ThreadSafePrettifyParser.INSTANCE.parse(lang, content);
    } catch (StackOverflowError e) {
      // TODO(dborowitz): Aaagh. Make prettify use RE2. Or replace it something
      // else. Or something.
      log.warn("StackOverflowError prettifying " + view.toUrl());
      return ImmutableList.of(
          new ParseResult(0, content.length(), ImmutableList.of(Prettify.PR_PLAIN)));
    }
  }

  private static void writeResult(SoyListData lines, String classes, String s, int start, int end) {
    SoyListData line = lines.getListData(lines.length() - 1);
    while (true) {
      int nl = nextLineBreak(s, start, end);
      if (nl < 0) {
        break;
      }
      addSpan(line, classes, s, start, nl);

      start = nl + 1;
      if (start == s.length()) {
        return;
      }
      line = new SoyListData();
      lines.add(line);
    }
    addSpan(line, classes, s, start, end);
  }

  private static void addSpan(SoyListData line, String classes, String s, int start, int end) {
    if (end - start > 0) {
      if (Strings.isNullOrEmpty(classes)) {
        classes = Prettify.PR_PLAIN;
      }
      line.add(new SoyMapData("classes", classes, "text", s.substring(start, end)));
    }
  }

  private static int nextLineBreak(String s, int start, int end) {
    int n = s.indexOf('\n', start);
    return n < end ? n : -1;
  }

  private static String extension(String path, String content) {
    if (content.startsWith("#!/bin/sh") || content.startsWith("#!/bin/bash")) {
      return "sh";
    } else if (content.startsWith("#!/usr/bin/perl")) {
      return "pl";
    } else if (content.startsWith("#!/usr/bin/python")) {
      return "py";
    } else if (path == null) {
      return null;
    }

    int slash = path.lastIndexOf('/');
    int dot = path.lastIndexOf('.');
    String ext = ((0 < dot) && (slash < dot)) ? path.substring(dot + 1) : null;
    if ("txt".equalsIgnoreCase(ext)) {
      return null;
    } else if ("mk".equalsIgnoreCase(ext)) {
      return "sh";
    } else if ("Makefile".equalsIgnoreCase(path)
        || ((0 < slash) && "Makefile".equalsIgnoreCase(path.substring(slash + 1)))) {
      return "sh";
    } else {
      return ext;
    }
  }
}
