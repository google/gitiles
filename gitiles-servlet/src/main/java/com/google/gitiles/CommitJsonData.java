// Copyright (C) 2013 Google Inc. All Rights Reserved.
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

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;

import java.util.List;

class CommitJsonData {
  private static final GitDateFormatter dateFormatter = new GitDateFormatter(Format.DEFAULT);

  static class Commit {
    String commit;
    List<String> parents;
    Ident author;
    Ident committer;
    String message;
  }

  static class Ident {
    String name;
    String email;
    String time;
  }

  static Commit toJsonData(RevCommit c) {
    Commit result = new Commit();
    result.commit = c.name();
    result.parents = Lists.newArrayListWithCapacity(c.getParentCount());
    for (RevCommit parent : c.getParents()) {
      result.parents.add(parent.name());
    }
    result.author = toJsonData(c.getAuthorIdent());
    result.committer = toJsonData(c.getCommitterIdent());
    result.message = c.getFullMessage();
    return result;
  }

  private static Ident toJsonData(PersonIdent ident) {
    Ident result = new Ident();
    result.name = ident.getName();
    result.email = ident.getEmailAddress();
    result.time = dateFormatter.formatDate(ident);
    return result;
  }

  private CommitJsonData() {
  }
}
