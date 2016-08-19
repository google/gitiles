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

import org.commonmark.node.CustomBlock;
import org.commonmark.node.Heading;

/**
 * Multi-column layout delineated by {@code |||---|||}.
 *
 * <p>Each {@link Heading} or {@link BlockNote} within the layout begins a new {@link Column} in the
 * HTML.
 */
public class MultiColumnBlock extends CustomBlock {
  /** Grid is 12 columns wide. */
  public static final int GRID_WIDTH = 12;

  /** Column within a {@link MultiColumnBlock}. */
  public static class Column extends CustomBlock {
    int span;
    boolean empty;
  }
}
