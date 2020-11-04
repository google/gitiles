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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gitiles.GitilesRequestFailureException.FailureReason;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefAdvertiser;

/** Serves an HTML page with all the refs in a repository. */
public class RefServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  private final TimeCache timeCache;

  protected RefServlet(
      GitilesAccess.Factory accessFactory, Renderer renderer, TimeCache timeCache) {
    super(renderer, accessFactory);
    this.timeCache = checkNotNull(timeCache, "timeCache");
  }

  @Override
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res) throws IOException {
    if (!ViewFilter.getView(req).getPathPart().isEmpty()) {
      throw new GitilesRequestFailureException(FailureReason.INCORECT_PARAMETER);
    }
    List<Map<String, Object>> tags;
    try (RevWalk walk = new RevWalk(ServletUtils.getRepository(req))) {
      tags = getTagsSoyData(req, timeCache, walk, 0);
    }
    renderHtml(
        req,
        res,
        "gitiles.refsDetail",
        ImmutableMap.of("branches", getBranchesSoyData(req, 0), "tags", tags));
  }

  @Override
  protected void doGetText(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    RefsResult refs = getRefs(ServletUtils.getRepository(req).getRefDatabase(), view.getPathPart());
    TextRefAdvertiser adv = new TextRefAdvertiser(startRenderText(req, res));
    adv.setDerefTags(true);
    adv.send(refs.refs);
    adv.end();
  }

  @Override
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    RefsResult refs = getRefs(ServletUtils.getRepository(req).getRefDatabase(), view.getPathPart());
    Map<String, RefJsonData> jsonRefs = new LinkedHashMap<>();
    int prefixLen = refs.prefix.length();
    for (Ref ref : refs.refs) {
      jsonRefs.put(ref.getName().substring(prefixLen), new RefJsonData(ref));
    }
    renderJson(req, res, jsonRefs, new TypeToken<Map<String, RefJsonData>>() {}.getType());
  }

  static List<Map<String, Object>> getBranchesSoyData(HttpServletRequest req, int limit)
      throws IOException {
    RefDatabase refdb = ServletUtils.getRepository(req).getRefDatabase();
    Ref head = refdb.exactRef(Constants.HEAD);
    Ref headLeaf = head != null && head.isSymbolic() ? head.getLeaf() : null;
    return getRefsSoyData(
        refdb,
        ViewFilter.getView(req),
        Constants.R_HEADS,
        branchComparator(headLeaf),
        headLeaf,
        limit);
  }

  private static Ordering<Ref> branchComparator(Ref headLeaf) {
    if (headLeaf == null) {
      return Ordering.from(RefComparator.INSTANCE);
    }
    final String headLeafName = headLeaf.getName();
    return new Ordering<Ref>() {
      @Override
      public int compare(@Nullable Ref left, @Nullable Ref right) {
        int l = isHead(left) ? 1 : 0;
        int r = isHead(right) ? 1 : 0;
        return r - l;
      }

      private boolean isHead(Ref ref) {
        return ref != null && ref.getName().equals(headLeafName);
      }
    }.compound(RefComparator.INSTANCE);
  }

  static List<Map<String, Object>> getTagsSoyData(
      HttpServletRequest req, TimeCache timeCache, RevWalk walk, int limit) throws IOException {
    return getRefsSoyData(
        ServletUtils.getRepository(req).getRefDatabase(),
        ViewFilter.getView(req),
        Constants.R_TAGS,
        tagComparator(timeCache, walk),
        null,
        limit);
  }

  private static Long getTime(RevWalk walk, TimeCache timeCache, Ref ref) {
    try {
      return timeCache.getTime(walk, ref.getObjectId());
    } catch (IOException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  private static Ordering<Ref> tagComparator(TimeCache timeCache, RevWalk walk) {
    return Ordering.natural()
        .onResultOf((Ref r) -> getTime(walk, timeCache, r))
        .reverse()
        .compound(RefComparator.INSTANCE);
  }

  private static List<Map<String, Object>> getRefsSoyData(
      RefDatabase refdb,
      GitilesView view,
      String prefix,
      Ordering<Ref> ordering,
      @Nullable Ref headLeaf,
      int limit)
      throws IOException {
    checkArgument(prefix.endsWith("/"), "ref hierarchy prefix should end with /: %s", prefix);
    Collection<Ref> refs = refdb.getRefsByPrefix(prefix);
    refs = ordering.leastOf(refs, limit > 0 ? Ints.saturatedCast(limit + 1L) : refs.size());
    List<Map<String, Object>> result = Lists.newArrayListWithCapacity(refs.size());

    for (Ref ref : refs) {
      String name = ref.getName().substring(prefix.length());
      Map<String, Object> value = Maps.newHashMapWithExpectedSize(3);
      value.put(
          "url",
          GitilesView.revision()
              .copyFrom(view)
              .setRevision(Revision.unpeeled(ref.getName(), ref.getObjectId()))
              .toUrl());
      value.put("name", name);
      if (headLeaf != null) {
        value.put("isHead", headLeaf.equals(ref));
      }
      result.add(value);
    }

    return result;
  }

  static String sanitizeRefForText(String refName) {
    return refName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static class RefsResult {
    String prefix;
    List<Ref> refs;

    RefsResult(String prefix, List<Ref> refs) {
      this.prefix = prefix;
      this.refs = refs;
    }
  }

  private static RefsResult getRefs(RefDatabase refdb, String path) throws IOException {
    path = GitilesView.maybeTrimLeadingAndTrailingSlash(path);
    if (path.isEmpty()) {
      return new RefsResult(path, refdb.getRefs());
    }
    path = Constants.R_REFS + path;
    Ref singleRef = refdb.exactRef(path);
    if (singleRef != null) {
      return new RefsResult("", ImmutableList.of(singleRef));
    }
    path = path + '/';
    return new RefsResult(path, refdb.getRefsByPrefix(path));
  }

  private static class TextRefAdvertiser extends RefAdvertiser {
    private final Writer writer;

    private TextRefAdvertiser(Writer writer) {
      this.writer = writer;
    }

    @Override
    public void advertiseId(AnyObjectId id, String refName) throws IOException {
      super.advertiseId(id, sanitizeRefForText(refName));
    }

    @Override
    protected void writeOne(CharSequence line) throws IOException {
      writer.append(line);
    }

    @Override
    public void end() throws IOException {
      writer.close();
    }
  }

  static class RefJsonData {
    RefJsonData(Ref ref) {
      value = ref.getObjectId().getName();
      if (ref.getPeeledObjectId() != null) {
        peeled = ref.getPeeledObjectId().getName();
      }
      if (ref.isSymbolic()) {
        target = ref.getTarget().getName();
      }
    }

    String value;
    String peeled;
    String target;
  }
}
