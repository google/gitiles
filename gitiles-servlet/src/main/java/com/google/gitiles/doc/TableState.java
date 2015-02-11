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

import org.pegdown.ast.TableCellNode;
import org.pegdown.ast.TableColumnNode;
import org.pegdown.ast.TableNode;

import java.util.List;

class TableState {
  private final List<TableColumnNode> columns;

  boolean inHeader;
  int column;

  TableState(TableNode node) {
    columns = node.getColumns();
  }

  void startRow() {
    column = 0;
  }

  String getAlign() {
    int pos = Math.min(column, columns.size() - 1);
    TableColumnNode c = columns.get(pos);
    switch (c.getAlignment()) {
      case None:
        return null;
      case Left:
        return "left";
      case Right:
        return "right";
      case Center:
        return "center";
      default:
        throw new IllegalStateException(String.format(
            "unsupported alignment %s on column %d",
            c.getAlignment(), pos));
    }
  }

  void done(TableCellNode cell) {
    column += cell.getColSpan();
  }
}