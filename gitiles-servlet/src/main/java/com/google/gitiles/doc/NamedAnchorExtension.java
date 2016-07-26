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

import org.commonmark.Extension;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.Parser.ParserExtension;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses <code>{#foo}</code> into {@link NamedAnchor}. */
public class NamedAnchorExtension implements ParserExtension {
  public static Extension create() {
    return new NamedAnchorExtension();
  }

  private NamedAnchorExtension() {}

  @Override
  public void extend(Parser.Builder builder) {
    builder.customDelimiterProcessor(new Processor());
  }

  private static class Processor implements DelimiterProcessor {
    private static final Pattern ID = Pattern.compile("#([^\\s}]+)");

    @Override
    public char getOpeningCharacter() {
      return '{';
    }

    @Override
    public char getClosingCharacter() {
      return '}';
    }

    @Override
    public int getMinLength() {
      return 1;
    }

    @Override
    public int getDelimiterUse(DelimiterRun opener, DelimiterRun closer) {
      return 1;
    }

    @Override
    public void process(Text opener, Text closer, int delimiterUse) {
      Node content = opener.getNext();
      if (content instanceof Text && content.getNext() == closer) {
        Matcher m = ID.matcher(((Text) content).getLiteral());
        if (m.matches()) {
          content.unlink();

          NamedAnchor anchor = new NamedAnchor();
          anchor.setName(m.group(1));
          opener.insertAfter(anchor);
          MarkdownUtil.trimPreviousWhitespace(opener);
          return;
        }
      }

      // If its not exactly one well formed Text node; restore the delimiter text.
      opener.insertAfter(new Text("{"));
      closer.insertBefore(new Text("}"));
    }
  }
}
