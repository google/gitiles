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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Strings;
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

/** Serves the index page for a repository, if accessed directly by a browser. */
public class RepositoryIndexServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  private final GitilesAccess.Factory accessFactory;
  private final TimeCache timeCache;

  public RepositoryIndexServlet(Renderer renderer, GitilesAccess.Factory accessFactory,
      TimeCache timeCache) {
    super(renderer);
    this.accessFactory = checkNotNull(accessFactory, "accessFactory");
    this.timeCache = checkNotNull(timeCache, "timeCache");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    render(req, res, "gitiles.repositoryIndex", buildData(req));
  }

  @VisibleForTesting
  Map<String, ?> buildData(HttpServletRequest req) throws IOException {
    RepositoryDescription desc = accessFactory.forRequest(req).getRepositoryDescription();
    RevWalk walk = new RevWalk(ServletUtils.getRepository(req));
    List<Map<String, String>> tags;
    try {
      tags = getRefs(req, Constants.R_TAGS, tagComparator(walk));
    } finally {
      walk.release();
    }
    return ImmutableMap.of("cloneUrl", desc.cloneUrl,
        "mirroredFromUrl", Strings.nullToEmpty(desc.mirroredFromUrl),
        "description", Strings.nullToEmpty(desc.description),
        "branches", getRefs(req, Constants.R_HEADS, Ordering.from(RefComparator.INSTANCE)),
        "tags", tags);
  }

  private List<Map<String, String>> getRefs(HttpServletRequest req, String prefix,
      Ordering<Ref> ordering) throws IOException {
    RefDatabase refdb = ServletUtils.getRepository(req).getRefDatabase();
    Collection<Ref> refs = ordering.sortedCopy(refdb.getRefs(prefix).values());
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

  private Ordering<Ref> tagComparator(final RevWalk walk) {
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
}
