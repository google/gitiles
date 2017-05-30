// Copyright (C) 2017 Google Inc. All Rights Reserved.
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

import org.eclipse.jgit.lib.ObjectId;

class FileJsonData {
  static class File {
    String id;
    String repo;
    String revision;
    String path;
  }

  static File toJsonData(ObjectId id, String repo, String revision, String path) {
    File file = new File();
    file.id = id.name();
    file.repo = repo;
    file.revision = revision;
    file.path = path;
    return file;
  }

  private FileJsonData() {}
}
