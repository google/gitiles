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

import com.google.common.collect.ImmutableSet;
import org.commonmark.Extension;
import org.commonmark.node.Block;
import org.commonmark.parser.Parser;
import org.commonmark.parser.Parser.ParserExtension;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

/**
 * CommonMark extension for block notes.
 * <pre>
 * *** note
 * This is a note.
 * ***
 * </pre>
 */
public class BlockNoteExtension implements ParserExtension {
  private static final ImmutableSet<String> VALID_STYLES =
      ImmutableSet.of("note", "aside", "promo");

  public static Extension create() {
    return new BlockNoteExtension();
  }

  private BlockNoteExtension() {}

  @Override
  public void extend(Parser.Builder builder) {
    builder.customBlockParserFactory(new NoteParserFactory());
  }

  private static class NoteParser extends AbstractBlockParser {
    private final int indent;
    private final BlockNote block;
    private boolean done;

    NoteParser(int indent, String style) {
      this.indent = indent;
      block = new BlockNote();
      block.setClassName(style);
    }

    @Override
    public Block getBlock() {
      return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
      if (done) {
        return BlockContinue.none();
      }
      if (state.getIndent() <= indent) {
        int s = state.getNextNonSpaceIndex();
        CharSequence line = state.getLine();
        if ("***".contentEquals(line.subSequence(s, line.length()))) {
          done = true;
          return BlockContinue.atIndex(line.length());
        }
      }
      return BlockContinue.atIndex(state.getIndex());
    }

    @Override
    public boolean isContainer() {
      return true;
    }

    @Override
    public boolean canContain(Block block) {
      return true;
    }
  }

  private static class NoteParserFactory extends AbstractBlockParserFactory {
    @Override
    public BlockStart tryStart(ParserState state, MatchedBlockParser matched) {
      int s = state.getNextNonSpaceIndex();
      CharSequence line = state.getLine();
      CharSequence text = line.subSequence(s, line.length());
      if (text.length() < 3 || !"***".contentEquals(text.subSequence(0, 3))) {
        return BlockStart.none();
      }

      String style = text.subSequence(3, text.length()).toString().trim();
      if (!VALID_STYLES.contains(style)) {
        return BlockStart.none();
      }

      return BlockStart.of(new NoteParser(state.getIndent(), style)).atIndex(line.length());
    }
  }
}
