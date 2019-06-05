// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles.doc;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.util.StringUtils;

public class MarkdownConfig {
  public static final int IMAGE_LIMIT = 256 << 10;

  public static MarkdownConfig get(Config cfg) {
    return cfg.get(CONFIG_PARSER);
  }

  private static final SectionParser<MarkdownConfig> CONFIG_PARSER =
      new SectionParser<MarkdownConfig>() {
        @Override
        public MarkdownConfig parse(Config cfg) {
          return new MarkdownConfig(cfg);
        }
      };

  public final boolean render;
  public final int inputLimit;

  final int imageLimit;
  final String analyticsId;

  final boolean autoLink;
  final boolean blockNote;
  final boolean ghThematicBreak;
  final boolean multiColumn;
  final boolean namedAnchor;
  final boolean safeHtml;
  final boolean smartQuote;
  final boolean strikethrough;
  final boolean tables;
  final boolean toc;

  private final boolean allowAnyIFrame;
  private final ImmutableList<String> allowIFrame;

  MarkdownConfig(Config cfg) {
    render = cfg.getBoolean("markdown", "render", true);
    inputLimit = cfg.getInt("markdown", "inputLimit", 5 << 20);
    imageLimit = cfg.getInt("markdown", "imageLimit", IMAGE_LIMIT);
    analyticsId = Strings.emptyToNull(cfg.getString("google", null, "analyticsId"));

    boolean githubFlavor = cfg.getBoolean("markdown", "githubFlavor", true);
    autoLink = cfg.getBoolean("markdown", "autolink", githubFlavor);
    blockNote = cfg.getBoolean("markdown", "blocknote", false);
    ghThematicBreak = cfg.getBoolean("markdown", "ghthematicbreak", githubFlavor);
    multiColumn = cfg.getBoolean("markdown", "multicolumn", false);
    namedAnchor = cfg.getBoolean("markdown", "namedanchor", false);
    safeHtml = cfg.getBoolean("markdown", "safehtml", githubFlavor);
    smartQuote = cfg.getBoolean("markdown", "smartquote", false);
    strikethrough = cfg.getBoolean("markdown", "strikethrough", githubFlavor);
    tables = cfg.getBoolean("markdown", "tables", githubFlavor);
    toc = cfg.getBoolean("markdown", "toc", true);

    String[] f = {};
    if (safeHtml) {
      f = cfg.getStringList("markdown", null, "allowiframe");
    }
    allowAnyIFrame = f.length == 1 && Boolean.TRUE.equals(StringUtils.toBooleanOrNull(f[0]));
    if (allowAnyIFrame) {
      allowIFrame = ImmutableList.of();
    } else {
      allowIFrame = ImmutableList.copyOf(f);
    }
  }

  private MarkdownConfig(MarkdownConfig p, Set<String> enable, Set<String> disable) {
    render = p.render;
    inputLimit = p.inputLimit;
    imageLimit = p.imageLimit;
    analyticsId = p.analyticsId;

    autoLink = on("autolink", p.autoLink, enable, disable);
    blockNote = on("blocknote", p.blockNote, enable, disable);
    ghThematicBreak = on("ghthematicbreak", p.ghThematicBreak, enable, disable);
    multiColumn = on("multicolumn", p.multiColumn, enable, disable);
    namedAnchor = on("namedanchor", p.namedAnchor, enable, disable);
    safeHtml = on("safehtml", p.safeHtml, enable, disable);
    smartQuote = on("smartquote", p.smartQuote, enable, disable);
    strikethrough = on("strikethrough", p.strikethrough, enable, disable);
    tables = on("tables", p.tables, enable, disable);
    toc = on("toc", p.toc, enable, disable);

    allowAnyIFrame = safeHtml ? p.allowAnyIFrame : false;
    allowIFrame = safeHtml ? p.allowIFrame : ImmutableList.of();
  }

  private static boolean on(String key, boolean val, Set<String> enable, Set<String> disable) {
    return enable.contains(key) ? true : disable.contains(key) ? false : val;
  }

  boolean isIFrameAllowed(String src) {
    if (allowAnyIFrame) {
      return true;
    }
    for (String url : allowIFrame) {
      if (url.equals(src) || (url.endsWith("/") && src.startsWith(url))) {
        return true;
      }
    }
    return false;
  }

  MarkdownConfig copyWithExtensions(Set<String> enable, Set<String> disable) {
    return new MarkdownConfig(this, enable, disable);
  }
}
