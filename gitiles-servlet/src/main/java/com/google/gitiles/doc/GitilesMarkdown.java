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

import com.google.gitiles.GitilesView;

import org.parboiled.Rule;
import org.pegdown.Parser;
import org.pegdown.ParsingTimeoutException;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.RootNode;
import org.pegdown.plugins.BlockPluginParser;
import org.pegdown.plugins.PegDownPlugins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Additional markdown extensions known to Gitiles.
 * <p>
 * {@code [TOC]} as a stand-alone block will insert a table of contents
 * for the current document.
 */
class GitilesMarkdown extends Parser implements BlockPluginParser {
  private static final Logger log = LoggerFactory.getLogger(MarkdownHelper.class);

  // SUPPRESS_ALL_HTML is enabled to permit hosting arbitrary user content
  // while avoiding XSS style HTML, CSS and JavaScript injection attacks.
  //
  // HARDWRAPS is disabled to permit line wrapping within paragraphs to
  // make the source file easier to read in 80 column terminals without
  // this impacting the rendered formatting.
  private static final int MD_OPTIONS = (ALL | SUPPRESS_ALL_HTML) & ~(HARDWRAPS);

  static RootNode parseFile(GitilesView view, String path, String md) {
    if (md == null) {
      return null;
    }

    try {
      return newParser().parseMarkdown(md.toCharArray());
    } catch (ParsingTimeoutException e) {
      log.error("timeout rendering {}/{} at {}",
          view.getRepositoryName(),
          path,
          view.getRevision().getName());
      return null;
    }
  }

  private static PegDownProcessor newParser() {
    PegDownPlugins plugins = new PegDownPlugins.Builder()
        .withPlugin(GitilesMarkdown.class)
        .build();
    return new PegDownProcessor(MD_OPTIONS, plugins);
  }

  GitilesMarkdown() {
    super(MD_OPTIONS, 2000L, DefaultParseRunnerProvider);
  }

  @Override
  public Rule[] blockPluginRules() {
    return new Rule[]{ toc() };
  }

  public Rule toc() {
    return NodeSequence(
        string("[TOC]"),
        push(new TocNode()));
  }
}
