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

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TAG;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gitiles.CommitSoyData.KeySet;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves an HTML page with detailed information about a ref. */
public class RevisionServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(RevisionServlet.class);

  private final Linkifier linkifier;

  public RevisionServlet(Renderer renderer, Linkifier linkifier) {
    super(renderer);
    this.linkifier = checkNotNull(linkifier, "linkifier");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);

    RevWalk walk = new RevWalk(repo);
    try {
      List<RevObject> objects = listObjects(walk, view.getRevision());
      List<Map<String, ?>> soyObjects = Lists.newArrayListWithCapacity(objects.size());
      boolean hasBlob = false;

      // TODO(sop): Allow caching commits by SHA-1 when no S cookie is sent.
      for (RevObject obj : objects) {
        try {
          switch (obj.getType()) {
            case OBJ_COMMIT:
              soyObjects.add(ImmutableMap.of(
                  "type", Constants.TYPE_COMMIT,
                  "data", new CommitSoyData(linkifier, req, repo, walk, view)
                      .toSoyData((RevCommit) obj, KeySet.DETAIL_DIFF_TREE)));
              break;
            case OBJ_TREE:
              soyObjects.add(ImmutableMap.of(
                  "type", Constants.TYPE_TREE,
                  "data", new TreeSoyData(walk, view).toSoyData(obj)));
              break;
            case OBJ_BLOB:
              soyObjects.add(ImmutableMap.of(
                  "type", Constants.TYPE_BLOB,
                  "data", new BlobSoyData(walk, view).toSoyData(obj)));
              hasBlob = true;
              break;
            case OBJ_TAG:
              soyObjects.add(ImmutableMap.of(
                  "type", Constants.TYPE_TAG,
                  "data", new TagSoyData(linkifier, req).toSoyData((RevTag) obj)));
              break;
            default:
              log.warn("Bad object type for %s: %s", ObjectId.toString(obj.getId()), obj.getType());
              res.setStatus(SC_NOT_FOUND);
              return;
          }
        } catch (MissingObjectException e) {
          log.warn("Missing object " + ObjectId.toString(obj.getId()), e);
          res.setStatus(SC_NOT_FOUND);
          return;
        } catch (IncorrectObjectTypeException e) {
          log.warn("Incorrect object type for " + ObjectId.toString(obj.getId()), e);
          res.setStatus(SC_NOT_FOUND);
          return;
        }
      }

      render(req, res, "gitiles.revisionDetail", ImmutableMap.of(
          "title", view.getRevision().getName(),
          "objects", soyObjects,
          "hasBlob", hasBlob));
    } finally {
      walk.release();
    }
  }

  // TODO(dborowitz): Extract this.
  static List<RevObject> listObjects(RevWalk walk, Revision rev)
      throws MissingObjectException, IOException {
    List<RevObject> objects = Lists.newArrayListWithExpectedSize(1);
    ObjectId id = rev.getId();
    RevObject cur;
    while (true) {
      cur = walk.parseAny(id);
      objects.add(cur);
      if (cur.getType() != Constants.OBJ_TAG) {
        break;
      }
      id = ((RevTag) cur).getObject();
    }
    if (cur.getType() == Constants.OBJ_COMMIT) {
      objects.add(walk.parseTree(((RevCommit) cur).getTree()));
    }
    return objects;
  }
}
