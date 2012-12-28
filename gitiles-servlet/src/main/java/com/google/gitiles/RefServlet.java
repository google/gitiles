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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.UncheckedExecutionException;

import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves an HTML page with all the refs in a repository. */
public class RefServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  private final TimeCache timeCache;

  protected RefServlet(Renderer renderer, TimeCache timeCache) {
    super(renderer);
    this.timeCache = checkNotNull(timeCache, "timeCache");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    RevWalk walk = new RevWalk(ServletUtils.getRepository(req));
    List<Map<String, String>> tags;
    try {
      tags = getTags(req, timeCache, walk, 0);
    } finally {
      walk.release();
    }
    render(req, res, "gitiles.refsDetail", ImmutableMap.of(
        "branches", getBranches(req, 0),
        "tags", tags));
  }

  static List<Map<String, String>> getBranches(HttpServletRequest req, int limit)
      throws IOException {
    return getRefs(req, Constants.R_HEADS, Ordering.from(RefComparator.INSTANCE), limit);
  }

  static List<Map<String, String>> getTags(HttpServletRequest req, TimeCache timeCache,
     RevWalk walk, int limit) throws IOException {
    return getRefs(req, Constants.R_TAGS, tagComparator(timeCache, walk), limit);
  }

  private static Ordering<Ref> tagComparator(final TimeCache timeCache, final RevWalk walk) {
    return Ordering.natural().onResultOf(new Function<Ref, Long>() {
      @Override
      public Long apply(Ref ref) {
        try {
          return timeCache.getTime(walk, ref.getObjectId());
        } catch (IOException e) {
          throw new UncheckedExecutionException(e);
        }
      }
    }).reverse().compound(RefComparator.INSTANCE);
  }

  private static List<Map<String, String>> getRefs(HttpServletRequest req, String prefix,
      Ordering<Ref> ordering, int limit) throws IOException {
    RefDatabase refdb = ServletUtils.getRepository(req).getRefDatabase();
    Collection<Ref> refs = refdb.getRefs(prefix).values();
    refs = ordering.leastOf(refs, limit > 0 ? limit + 1 : refs.size());
    List<Map<String, String>> result = Lists.newArrayListWithCapacity(refs.size());

    for (Ref ref : refs) {
      String name = ref.getName().substring(prefix.length());
      boolean needPrefix = !ref.getName().equals(refdb.getRef(name).getName());
      result.add(ImmutableMap.of(
          "url", GitilesView.revision().copyFrom(req).setRevision(
              Revision.unpeeled(needPrefix ? ref.getName() : name, ref.getObjectId())).toUrl(),
          "name", name));
    }
    return result;
  }
}
