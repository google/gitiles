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

import org.pegdown.ast.AbstractNode;
import org.pegdown.ast.Node;

import java.util.Collections;
import java.util.List;

class NamedAnchorNode extends AbstractNode {
  final String name;

  NamedAnchorNode(String name) {
    this.name = name;
  }

  @Override
  public void accept(org.pegdown.ast.Visitor visitor) {
    ((Visitor) visitor).visit(this);
  }

  @Override
  public List<Node> getChildren() {
    return Collections.emptyList();
  }
}
