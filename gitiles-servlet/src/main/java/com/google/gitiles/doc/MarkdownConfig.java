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
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.util.StringUtils;

public class MarkdownConfig {
  public static final int IMAGE_LIMIT = 256 << 10;

  public static MarkdownConfig get(Config cfg) {
    return cfg.get(CONFIG_PARSER);
  }

  private static SectionParser<MarkdownConfig> CONFIG_PARSER =
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

  private final boolean allowAnyIFrame;
  private final ImmutableList<String> allowIFrame;

  MarkdownConfig(Config cfg) {
    render = cfg.getBoolean("markdown", "render", true);
    inputLimit = cfg.getInt("markdown", "inputLimit", 5 << 20);
    imageLimit = cfg.getInt("markdown", "imageLimit", IMAGE_LIMIT);
    analyticsId = Strings.emptyToNull(cfg.getString("google", null, "analyticsId"));

    String[] f = cfg.getStringList("markdown", null, "allowiframe");
    allowAnyIFrame = f.length == 1 && StringUtils.toBooleanOrNull(f[0]) == Boolean.TRUE;
    if (allowAnyIFrame) {
      allowIFrame = ImmutableList.of();
    } else {
      allowIFrame = ImmutableList.copyOf(f);
    }
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
}
