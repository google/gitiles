// Copyright (C) 2014 Google Inc. All Rights Reserved.
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

package com.google.gitiles.blame;

import static com.google.common.base.Preconditions.checkArgument;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.Serializable;

/** Region of the blame of a file. */
public class Region implements Serializable, Comparable<Region> {
  private static final long serialVersionUID = 1L;

  private final String sourcePath;
  private final ObjectId sourceCommit;
  private final PersonIdent sourceAuthor;
  private final int count;
  private transient int start;

  public Region(String path, ObjectId commit, PersonIdent author, int start, int end) {
    checkArgument((path != null && commit != null && author != null)
        || (path == null && commit == null && author == null),
        "expected all null or none: %s, %s, %s", path, commit, author);
    this.sourcePath = path;
    this.sourceCommit = commit;
    this.sourceAuthor = author;
    this.start = start;
    this.count = end - start;
  }

  int getStart() {
    return start;
  }

  int getEnd() {
    return start + count;
  }

  public int getCount() {
    return count;
  }

  public String getSourcePath() {
    return sourcePath;
  }

  public ObjectId getSourceCommit() {
    return sourceCommit;
  }

  public PersonIdent getSourceAuthor() {
    return sourceAuthor;
  }

  @Override
  public int compareTo(Region o) {
    return start - o.start;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (sourceCommit != null) {
      sb.append(sourceCommit.name().substring(0, 7))
          .append(' ')
          .append(sourceAuthor.toExternalString())
          .append(" (").append(sourcePath).append(')');
    } else {
      sb.append("<unblamed region>");
    }
    sb.append(' ')
        .append("start=").append(start)
        .append(", count=").append(count);
    return sb.toString();
  }
}
