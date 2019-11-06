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

package com.google.gitiles.blame.cache;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.hash;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.QuotedString;

/** Guava implementation of BlameCache, weighted by number of blame regions. */
public class BlameCacheImpl implements BlameCache {
  public static CacheBuilder<Key, List<Region>> defaultBuilder() {
    return weigher(CacheBuilder.newBuilder()).maximumWeight(10 << 10);
  }

  public static CacheBuilder<Key, List<Region>> weigher(
      CacheBuilder<? super Key, ? super List<Region>> builder) {
    return builder.weigher((k, v) -> v.size());
  }

  public static class Key {
    private final ObjectId commitId;
    private final String path;

    public Key(ObjectId commitId, String path) {
      this.commitId = commitId;
      this.path = path;
    }

    public ObjectId getCommitId() {
      return commitId;
    }

    public String getPath() {
      return path;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Key) {
        Key k = (Key) o;
        return Objects.equals(commitId, k.commitId) && Objects.equals(path, k.path);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return hash(commitId, path);
    }

    @Override
    public String toString() {
      return commitId.name() + ":" + QuotedString.GIT_PATH.quote(path);
    }
  }

  private final Cache<Key, List<Region>> cache;

  public BlameCacheImpl() {
    this(defaultBuilder());
  }

  public Cache<Key, List<Region>> getCache() {
    return cache;
  }

  public Callable<List<Region>> newLoader(Key key, Repository repo) {
    return () -> loadBlame(key, repo);
  }

  public BlameCacheImpl(CacheBuilder<? super Key, ? super List<Region>> builder) {
    this.cache = builder.build();
  }

  @Override
  public List<Region> get(Repository repo, ObjectId commitId, String path) throws IOException {
    try {
      Key key = new Key(commitId, path);
      return cache.get(key, newLoader(key, repo));
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  @Override
  public ObjectId findLastCommit(Repository repo, ObjectId commitId, String path)
      throws IOException {
    // Default implementation does no caching.
    try (RevWalk rw = new RevWalk(repo)) {
      rw.markStart(rw.parseCommit(commitId));
      rw.setRewriteParents(false);
      // Don't use rename detection, even though BlameGenerator does. It is not
      // possible for a commit to modify a path when not doing rename detection
      // but to not modify the same path when taking renames into account.
      rw.setTreeFilter(
          AndTreeFilter.create(PathFilterGroup.createFromStrings(path), TreeFilter.ANY_DIFF));
      return rw.next();
    }
  }

  public static List<Region> loadBlame(Key key, Repository repo) throws IOException {
    try (BlameGenerator gen = new BlameGenerator(repo, key.path)) {
      gen.push(null, key.commitId);
      if (gen.getResultContents() == null) {
        return ImmutableList.of();
      }
      return loadRegions(gen);
    }
  }

  private static class PooledCommit {
    final ObjectId commit;
    final PersonIdent author;

    private PooledCommit(ObjectId commit, PersonIdent author) {
      this.commit = commit;
      this.author = author;
    }
  }

  private static List<Region> loadRegions(BlameGenerator gen) throws IOException {
    Map<ObjectId, PooledCommit> commits = Maps.newHashMap();
    Interner<String> strings = Interners.newStrongInterner();
    int lineCount = gen.getResultContents().size();

    List<Region> regions = Lists.newArrayList();
    while (gen.next()) {
      String path = gen.getSourcePath();
      PersonIdent author = gen.getSourceAuthor();
      ObjectId commit = gen.getSourceCommit();
      checkState(path != null && author != null && commit != null);

      PooledCommit pc = commits.get(commit);
      if (pc == null) {
        pc =
            new PooledCommit(
                commit.copy(),
                new PersonIdent(
                    strings.intern(author.getName()),
                    strings.intern(author.getEmailAddress()),
                    author.getWhen(),
                    author.getTimeZone()));
        commits.put(pc.commit, pc);
      }
      path = strings.intern(path);
      commit = pc.commit;
      author = pc.author;
      regions.add(new Region(path, commit, author, gen.getResultStart(), gen.getResultEnd()));
    }
    Collections.sort(regions);

    // Fill in any gaps left by bugs in JGit, since rendering code assumes the
    // full set of contiguous regions.
    List<Region> result = Lists.newArrayListWithExpectedSize(regions.size());
    Region last = null;
    for (Region r : regions) {
      if (last != null) {
        checkState(last.getEnd() <= r.getStart());
        if (last.getEnd() < r.getStart()) {
          result.add(new Region(null, null, null, last.getEnd(), r.getStart()));
        }
      }
      result.add(r);
      last = r;
    }
    if (last != null && last.getEnd() != lineCount) {
      result.add(new Region(null, null, null, last.getEnd(), lineCount));
    }

    return ImmutableList.copyOf(result);
  }
}
