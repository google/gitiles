// Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.gitiles.doc;

import java.util.ArrayList;
import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.eclipse.jgit.util.RawParseUtils;

/** Parses Gitiles style CommonMark Markdown. */
public class GitilesMarkdown {
  public static Node parse(MarkdownConfig cfg, byte[] md) {
    return parse(cfg, RawParseUtils.decode(md));
  }

  public static Node parse(MarkdownConfig cfg, String md) {
    List<Extension> ext = new ArrayList<>();
    if (cfg.autoLink) {
      ext.add(AutolinkExtension.create());
    }
    if (cfg.blockNote) {
      ext.add(BlockNoteExtension.create());
    }
    if (cfg.safeHtml) {
      ext.add(GitilesHtmlExtension.create());
    }
    if (cfg.ghThematicBreak) {
      ext.add(GitHubThematicBreakExtension.create());
    }
    if (cfg.multiColumn) {
      ext.add(MultiColumnExtension.create());
    }
    if (cfg.namedAnchor) {
      ext.add(NamedAnchorExtension.create());
    }
    if (cfg.smartQuote) {
      ext.add(SmartQuotedExtension.create());
    }
    if (cfg.strikethrough) {
      ext.add(StrikethroughExtension.create());
    }
    if (cfg.tables) {
      ext.add(TablesExtension.create());
    }
    if (cfg.toc) {
      ext.add(TocExtension.create());
    }
    return Parser.builder().extensions(ext).build().parse(md);
  }

  private GitilesMarkdown() {}
}
