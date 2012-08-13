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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.RefDatabase;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves the index page for a repository, if accessed directly by a browser. */
public class RepositoryIndexServlet extends BaseServlet {
  private final GitilesAccess.Factory accessFactory;

  public RepositoryIndexServlet(Renderer renderer, GitilesAccess.Factory accessFactory) {
    super(renderer);
    this.accessFactory = checkNotNull(accessFactory, "accessFactory");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    render(req, res, "gitiles.repositoryIndex", buildData(req));
  }

  @VisibleForTesting
  Map<String, ?> buildData(HttpServletRequest req) throws IOException {
    RepositoryDescription desc = accessFactory.forRequest(req).getRepositoryDescription();
    return ImmutableMap.of(
        "cloneUrl", desc.cloneUrl,
        "description", Strings.nullToEmpty(desc.description),
        "branches", getRefs(req, Constants.R_HEADS),
        "tags", getRefs(req, Constants.R_TAGS));
  }

  private List<Map<String, String>> getRefs(HttpServletRequest req, String prefix)
      throws IOException {
    RefDatabase refdb = ServletUtils.getRepository(req).getRefDatabase();
    String repoName = ViewFilter.getView(req).getRepositoryName();
    Collection<Ref> refs = RefComparator.sort(refdb.getRefs(prefix).values());
    List<Map<String, String>> result = Lists.newArrayListWithCapacity(refs.size());

    for (Ref ref : refs) {
      String name = ref.getName().substring(prefix.length());
      boolean needPrefix = !ref.getName().equals(refdb.getRef(name).getName());
      result.add(ImmutableMap.of(
          "url", GitilesView.log().copyFrom(req).setRevision(
              Revision.unpeeled(needPrefix ? ref.getName() : name, ref.getObjectId())).toUrl(),
          "name", name));
    }

    return result;
  }
}
