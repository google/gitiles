// Copyright 2014 Google Inc. All Rights Reserved.
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

package com.google.gitiles;

import com.google.common.annotations.VisibleForTesting;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.IOException;

/**
 * A {@link RevFilter} which only includes {@link RevCommit}s by an author pattern.
 *
 * Mostly equivalent to {@code git log --author}.
 */
public class AuthorRevFilter extends RevFilter {
  private final String authorPattern;

  public AuthorRevFilter(String authorPattern) {
    this.authorPattern = authorPattern;
  }

  @Override
  public boolean include(RevWalk walker, RevCommit commit) throws StopWalkException,
      MissingObjectException, IncorrectObjectTypeException, IOException {
    return matchesPerson(commit.getAuthorIdent());
  }

  /** @return whether the given person matches the author filter. */
  @VisibleForTesting
  boolean matchesPerson(PersonIdent person) {
    // Equivalent to --fixed-strings, to avoid pathological performance of Java
    // regex matching.
    // TODO(kalman): Find/use a port of re2.
    return person.getName().contains(authorPattern)
        || person.getEmailAddress().contains(authorPattern);
  }

  @Override
  public RevFilter clone() {
    return this;
  }
}
