// Copyright (C) 2014 Google Inc. All Rights Reserved.
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

import com.google.common.collect.Lists;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.List;

class TreeJsonData {
  static class Tree {
    String id;
    List<Entry> entries;
  }

  static class Entry {
    int mode;
    String type;
    String id;
    String name;
  }

  static Tree toJsonData(ObjectId id, TreeWalk tw) throws IOException {
    Tree tree = new Tree();
    tree.id = id.name();
    tree.entries = Lists.newArrayList();
    while (tw.next()) {
      Entry e = new Entry();
      FileMode mode = tw.getFileMode(0);
      e.mode = mode.getBits();
      e.type = Constants.typeString(mode.getObjectType());
      e.id = tw.getObjectId(0).name();
      e.name = tw.getNameString();
      tree.entries.add(e);
    }
    return tree;
  }

  private TreeJsonData() {
  }
}
