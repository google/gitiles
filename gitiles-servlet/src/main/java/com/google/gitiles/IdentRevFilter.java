// Copyright (C) 2014 The Android Open Source Project
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

/** Filter which only includes commits matching a person identity. */
public abstract class IdentRevFilter extends RevFilter {
  public static IdentRevFilter author(String author) {
    return new Author(author);
  }

  public static IdentRevFilter committer(String committer) {
    return new Committer(committer);
  }

  private final String pattern;

  protected IdentRevFilter(String pattern) {
    this.pattern = pattern;
  }

  @Override
  public boolean include(RevWalk walker, RevCommit commit) throws StopWalkException,
      MissingObjectException, IncorrectObjectTypeException, IOException {
    return matchesPerson(getIdent(commit));
  }

  @Override
  public RevFilter clone() {
    return this;
  }

  /** @return whether the given person matches the author filter. */
  @VisibleForTesting
  boolean matchesPerson(PersonIdent person) {
    // Equivalent to --fixed-strings, to avoid pathological performance of Java
    // regex matching.
    // TODO(kalman): Find/use a port of re2.
    return person.getName().contains(pattern)
        || person.getEmailAddress().contains(pattern);
  }

  protected abstract PersonIdent getIdent(RevCommit commit);

  private static class Author extends IdentRevFilter {
    private Author(String author) {
      super(author);
    }

    @Override
    protected PersonIdent getIdent(RevCommit commit) {
      return commit.getAuthorIdent();
    }
  }

  private static class Committer extends IdentRevFilter {
    private Committer(String committer) {
      super(committer);
    }

    @Override
    protected PersonIdent getIdent(RevCommit commit) {
      return commit.getCommitterIdent();
    }
  }
}
