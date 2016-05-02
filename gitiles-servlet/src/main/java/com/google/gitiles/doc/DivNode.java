// Copyright 2015 Google Inc. All Rights Reserved.
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

import org.pegdown.ast.Node;
import org.pegdown.ast.ParaNode;
import org.pegdown.ast.SuperNode;

import java.util.List;

/** Block note to render as {@code &lt;div class="clazz"&gt;}. */
public class DivNode extends SuperNode {
  private final String style;

  DivNode(String style, List<Node> list) {
    super(
        list.size() == 1 && list.get(0) instanceof ParaNode
            ? ((ParaNode) list.get(0)).getChildren()
            : list);
    this.style = style;
  }

  public String getStyleName() {
    return style;
  }

  @Override
  public void accept(org.pegdown.ast.Visitor visitor) {
    ((Visitor) visitor).visit(this);
  }
}
