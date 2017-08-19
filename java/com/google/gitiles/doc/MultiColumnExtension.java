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

import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;
import com.google.gitiles.doc.MultiColumnBlock.Column;
import java.util.ArrayList;
import java.util.List;
import org.commonmark.Extension;
import org.commonmark.node.Block;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.parser.Parser.ParserExtension;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

/** CommonMark extension for multicolumn layouts. */
public class MultiColumnExtension implements ParserExtension {
  private static final String MARKER = "|||---|||";

  public static Extension create() {
    return new MultiColumnExtension();
  }

  private MultiColumnExtension() {}

  @Override
  public void extend(Parser.Builder builder) {
    builder.customBlockParserFactory(new MultiColumnParserFactory());
  }

  private static class MultiColumnParser extends AbstractBlockParser {
    private final MultiColumnBlock block = new MultiColumnBlock();
    private final List<Column> cols;
    private boolean done;

    MultiColumnParser(String layout) {
      List<String> specList = Splitter.on(',').trimResults().splitToList(layout);
      cols = new ArrayList<>(specList.size());
      for (String spec : specList) {
        cols.add(parseColumn(spec));
      }
    }

    private MultiColumnBlock.Column parseColumn(String spec) {
      MultiColumnBlock.Column col = new MultiColumnBlock.Column();
      if (spec.startsWith(":")) {
        col.empty = true;
        spec = spec.substring(1);
      }

      Integer width = Ints.tryParse(spec, 10);
      if (width != null) {
        col.span = width;
      }
      return col;
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
      if (state.getIndent() == 0) {
        int s = state.getNextNonSpaceIndex();
        CharSequence line = state.getLine();
        if (MARKER.contentEquals(line.subSequence(s, line.length()))) {
          done = true;
          return BlockContinue.atIndex(line.length());
        }
      }
      return BlockContinue.atIndex(state.getIndex());
    }

    @Override
    public void closeBlock() {
      splitChildren();
      rebalanceSpans();

      for (MultiColumnBlock.Column c : cols) {
        block.appendChild(c);
      }
    }

    private void splitChildren() {
      int colIdx = 0;
      Column col = null;
      Node next = null;

      for (Node child = block.getFirstChild(); child != null; child = next) {
        if (col == null || child instanceof Heading || child instanceof BlockNote) {
          for (; ; ) {
            if (colIdx == cols.size()) {
              cols.add(new Column());
            }
            col = cols.get(colIdx++);
            if (!col.empty) {
              break;
            }
          }
        }
        next = child.getNext();
        col.appendChild(child);
      }
    }

    private void rebalanceSpans() {
      int remaining = MultiColumnBlock.GRID_WIDTH;
      for (int i = 0; i < cols.size(); i++) {
        Column col = cols.get(i);
        if (col.span <= 0 || col.span > MultiColumnBlock.GRID_WIDTH) {
          col.span = remaining / (cols.size() - i);
        }
        remaining = Math.max(0, remaining - col.span);
      }
    }

    @Override
    public boolean isContainer() {
      return true;
    }

    @Override
    public boolean canContain(Block block) {
      return !(block instanceof MultiColumnBlock);
    }
  }

  private static class MultiColumnParserFactory extends AbstractBlockParserFactory {
    @Override
    public BlockStart tryStart(ParserState state, MatchedBlockParser matched) {
      if (state.getIndent() > 0) {
        return BlockStart.none();
      }

      int s = state.getNextNonSpaceIndex();
      CharSequence line = state.getLine();
      CharSequence text = line.subSequence(s, line.length());
      if (text.length() < MARKER.length()
          || !MARKER.contentEquals(text.subSequence(0, MARKER.length()))) {
        return BlockStart.none();
      }

      String layout = text.subSequence(MARKER.length(), text.length()).toString().trim();
      return BlockStart.of(new MultiColumnParser(layout)).atIndex(text.length());
    }
  }
}
