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

import com.google.common.collect.ImmutableList;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.eclipse.jgit.util.RawParseUtils;

/** Parses Gitiles style CommonMark Markdown. */
public class GitilesMarkdown {
  private static final Parser PARSER =
      Parser.builder()
          .extensions(
              ImmutableList.of(
                  AutolinkExtension.create(),
                  BlockNoteExtension.create(),
                  GitilesHtmlExtension.create(),
                  GitHubThematicBreakExtension.create(),
                  MultiColumnExtension.create(),
                  NamedAnchorExtension.create(),
                  SmartQuotedExtension.create(),
                  StrikethroughExtension.create(),
                  TablesExtension.create(),
                  TocExtension.create()))
          .build();

  public static Node parse(byte[] md) {
    return parse(RawParseUtils.decode(md));
  }

  public static Node parse(String md) {
    return PARSER.parse(md);
  }

  private GitilesMarkdown() {}
}
