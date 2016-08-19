// Copyright 2014 Google Inc. All Rights Reserved.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts commit message text to soy data in accordance with a commentlink rule.
 *
 * <p>Example:
 *
 * <pre>
 *  new CommentLinkInfo(
 *      Pattern.compile("bug (\d+)"),
 *      "http://bugs/$1")
 *    .linkify("do something nice\n\nbug 5")
 * </pre>
 *
 * <p>returns a list of soy data objects:
 *
 * <pre>
 * ImmutableList.of(
 *   ImmutableMap.of("text", "do something nice\n\n"),
 *   ImmutableMap.of("text", "bug 5", "url", "http://bugs/5")
 * )
 * </pre>
 */
public class CommentLinkInfo {
  private final Pattern pattern;
  private final String link;

  public CommentLinkInfo(Pattern pattern, String link) {
    this.pattern = checkNotNull(pattern);
    this.link = checkNotNull(link);
  }

  public List<Map<String, String>> linkify(String input) {
    List<Map<String, String>> parsed = Lists.newArrayList();
    Matcher m = pattern.matcher(input);
    int last = 0;
    while (m.find()) {
      addText(parsed, input.substring(last, m.start()));
      String text = m.group(0);
      addLink(parsed, text, pattern.matcher(text).replaceAll(link));
      last = m.end();
    }
    addText(parsed, input.substring(last));
    return ImmutableList.copyOf(parsed);
  }

  private static void addLink(List<Map<String, String>> parts, String text, String url) {
    parts.add(ImmutableMap.of("text", text, "url", url));
  }

  private static void addText(List<Map<String, String>> parts, String text) {
    if (text.isEmpty()) {
      return;
    }
    if (parts.isEmpty()) {
      parts.add(ImmutableMap.of("text", text));
    } else {
      Map<String, String> old = parts.get(parts.size() - 1);
      if (!old.containsKey("url")) {
        parts.set(parts.size() - 1, ImmutableMap.of("text", old.get("text") + text));
      } else {
        parts.add(ImmutableMap.of("text", text));
      }
    }
  }
}
