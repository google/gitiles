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
import org.parboiled.support.StringBuilderVar;
import org.pegdown.Parser;
import org.pegdown.ParsingTimeoutException;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.Node;
import org.pegdown.ast.RootNode;
import org.pegdown.plugins.BlockPluginParser;
import org.pegdown.plugins.PegDownPlugins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Parses Gitiles extensions to markdown. */
class GitilesMarkdown extends Parser implements BlockPluginParser {
  private static final Logger log = LoggerFactory.getLogger(MarkdownUtil.class);

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

  private PegDownProcessor parser;

  GitilesMarkdown() {
    super(MD_OPTIONS, 2000L, DefaultParseRunnerProvider);
  }

  @Override
  public Rule[] blockPluginRules() {
    return new Rule[] {
        cols(),
        note(),
        toc(),
    };
  }

  public Rule toc() {
    return NodeSequence(
        string("[TOC]"),
        push(new TocNode()));
  }

  public Rule note() {
    StringBuilderVar body = new StringBuilderVar();
    return NodeSequence(
        string("***"), Sp(), typeOfNote(), Newline(),
        oneOrMore(
          testNot(string("***"), Newline()),
          Line(body)),
        string("***"), Newline(),
        push(new DivNode(popAsString(), parse(body))));
  }

  public Rule typeOfNote() {
    return firstOf(
        sequence(string("note"), push(match())),
        sequence(string("promo"), push(match())),
        sequence(string("aside"), push(match())));
  }

  public Rule cols() {
    StringBuilderVar body = new StringBuilderVar();
    return NodeSequence(
        colsTag(), Newline(),
        oneOrMore(
            testNot(colsTag(), Newline()),
            Line(body)),
        colsTag(), Newline(),
        push(new ColsNode(parse(body))));
  }

  public Rule colsTag() {
    return string("|||---|||");
  }

  public List<Node> parse(StringBuilderVar body) {
    // The pegdown code doesn't provide enough visibility to directly
    // use its existing parsing rules. Recurse manually for inner text
    // parsing within a block.
    if (parser == null) {
      parser = newParser();
    }
    return parser.parseMarkdown(body.getChars()).getChildren();
  }
}
