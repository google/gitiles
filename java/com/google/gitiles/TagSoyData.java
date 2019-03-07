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

import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/** Soy data converter for git tags. */
public class TagSoyData {
  private final Linkifier linkifier;
  private final HttpServletRequest req;

  public TagSoyData(Linkifier linkifier, HttpServletRequest req) {
    this.linkifier = linkifier;
    this.req = req;
  }

  public Map<String, Object> toSoyData(RevWalk walk, RevTag tag, DateFormatter df)
      throws MissingObjectException, IOException {
    walk.parseBody(tag);

    Map<String, Object> data = Maps.newHashMapWithExpectedSize(4);
    data.put("sha", ObjectId.toString(tag));
    if (tag.getTaggerIdent() != null) {
      data.put("tagger", CommitSoyData.toSoyData(tag.getTaggerIdent(), df));
    }
    data.put("object", ObjectId.toString(tag.getObject()));
    data.put("message", linkifier.linkify(req, tag.getFullMessage()));
    return data;
  }
}
