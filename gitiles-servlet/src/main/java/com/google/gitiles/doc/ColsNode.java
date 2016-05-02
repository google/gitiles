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

import org.pegdown.ast.HeaderNode;
import org.pegdown.ast.Node;
import org.pegdown.ast.SuperNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-column layout delineated by {@code |||---|||}.
 * <p>
 * Each header within the layout creates a new column in the HTML.
 */
public class ColsNode extends SuperNode {
  static final int GRID_WIDTH = 12;

  ColsNode(List<Column> spec, List<Node> children) {
    super(wrap(spec, children));
  }

  @Override
  public void accept(org.pegdown.ast.Visitor visitor) {
    ((Visitor) visitor).visit(this);
  }

  private static List<Node> wrap(List<Column> spec, List<Node> children) {
    List<Column> columns = copyOf(spec);
    splitChildren(columns, children);

    int remaining = GRID_WIDTH;
    for (int i = 0; i < columns.size(); i++) {
      Column col = columns.get(i);
      if (col.span <= 0 || col.span > GRID_WIDTH) {
        col.span = remaining / (columns.size() - i);
      }
      remaining = Math.max(0, remaining - col.span);
    }
    return asNodeList(columns);
  }

  private static void splitChildren(List<Column> columns, List<Node> children) {
    int idx = 0;
    Column col = null;
    for (Node n : children) {
      if (col == null || n instanceof HeaderNode || n instanceof DivNode) {
        for (; ; ) {
          if (idx < columns.size()) {
            col = columns.get(idx);
          } else {
            col = new Column();
            columns.add(col);
          }
          idx++;
          if (!col.empty) {
            break;
          }
        }
      }
      col.getChildren().add(n);
    }
  }

  private static <T> ArrayList<T> copyOf(List<T> in) {
    return in != null && !in.isEmpty() ? new ArrayList<>(in) : new ArrayList<T>();
  }

  @SuppressWarnings("unchecked")
  private static List<Node> asNodeList(List<? extends Node> columns) {
    return (List<Node>) columns;
  }

  static class Column extends SuperNode {
    int span;
    boolean empty;

    @Override
    public void accept(org.pegdown.ast.Visitor visitor) {
      ((Visitor) visitor).visit(this);
    }
  }
}
