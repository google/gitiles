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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gitiles.CommitData.Field;
import com.google.template.soy.tofu.SoyTofu;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

public class LogSoyData {
  private static final ImmutableSet<Field> FIELDS = Sets.immutableEnumSet(Field.ABBREV_SHA,
      Field.SHA, Field.URL, Field.SHORT_MESSAGE, Field.MESSAGE, Field.AUTHOR, Field.COMMITTER,
      Field.BRANCHES, Field.TAGS);
  private static final ImmutableSet<Field> VERBOSE_FIELDS = Field.setOf(FIELDS, Field.DIFF_TREE);

  private final HttpServletRequest req;
  private final GitilesView view;
  private final Set<Field> fields;
  private final String pretty;
  private final String variant;
  private CommitSoyData csd;

  public LogSoyData(HttpServletRequest req, GitilesAccess access, String pretty)
      throws IOException {
    this.req = checkNotNull(req);
    this.view = checkNotNull(ViewFilter.getView(req));
    this.pretty = checkNotNull(pretty);
    Config config = access.getConfig();
    fields = config.getBoolean("logFormat", pretty, "verbose", false) ? VERBOSE_FIELDS : FIELDS;
    variant = firstNonNull(config.getString("logFormat", pretty, "variant"), pretty);
  }

  public void renderStreaming(Paginator paginator, @Nullable String revision, Renderer renderer,
      Writer out, DateFormatter df) throws IOException {
    renderer.newRenderer("gitiles.logEntriesHeader")
        .setData(toHeaderSoyData(paginator, revision))
        .render(out);
    out.flush();

    SoyTofu.Renderer entryRenderer = renderer.newRenderer("gitiles.logEntryWrapper");
    boolean first = true;
    for (RevCommit c : paginator) {
      entryRenderer.setData(toEntrySoyData(paginator, c, df, first)).render(out);
      out.flush();
      first = false;
    }
    if (first) {
      renderer.newRenderer("gitiles.emptyLog").render(out);
    }

    renderer.newRenderer("gitiles.logEntriesFooter")
        .setData(toFooterSoyData(paginator, revision))
        .render(out);
  }

  private Map<String, Object> toHeaderSoyData(Paginator paginator, @Nullable String revision) {
    Map<String, Object> data = Maps.newHashMapWithExpectedSize(5);
    data.put("logEntryPretty", pretty);
    ObjectId prev = paginator.getPreviousStart();
    if (prev != null) {
      GitilesView.Builder prevView = copyAndCanonicalizeView(revision);
      if (!prevView.getRevision().getId().equals(prev)) {
        prevView.replaceParam(LogServlet.START_PARAM, prev.name());
      }
      data.put("previousUrl", prevView.toUrl());
    }
    return data;
  }

  private Map<String, Object> toEntrySoyData(Paginator paginator, RevCommit c, DateFormatter df,
      boolean first) throws IOException {
    if (csd == null) {
      csd = new CommitSoyData();
    }

    Map<String, Object> entry = csd.setRevWalk(paginator.getWalk())
        .toSoyData(req, c, fields, df);
    return ImmutableMap.of(
        "firstWithPrevious", first && paginator.getPreviousStart() != null,
        "variant", variant,
        "entry", entry);
  }

  private Map<String, Object> toFooterSoyData(Paginator paginator, @Nullable String revision) {
    Map<String, Object> data = Maps.newHashMapWithExpectedSize(1);
    ObjectId next = paginator.getNextStart();
    if (next != null) {
      data.put("nextUrl", copyAndCanonicalizeView(revision)
          .replaceParam(LogServlet.START_PARAM, next.name())
          .toUrl());
    }
    return data;
  }

  private GitilesView.Builder copyAndCanonicalizeView(String revision) {
    // Canonicalize the view by using full SHAs.
    GitilesView.Builder copy = GitilesView.log().copyFrom(view);
    if (view.getRevision() != Revision.NULL) {
      copy.setRevision(view.getRevision());
    } else {
      copy.setRevision(Revision.named(revision));
    }
    if (view.getOldRevision() != Revision.NULL) {
      copy.setOldRevision(view.getOldRevision());
    }
    return copy;
  }
}
