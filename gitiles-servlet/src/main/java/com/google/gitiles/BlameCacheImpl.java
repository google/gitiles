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

import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Guava implementation of BlameCache, weighted by number of blame regions. */
public class BlameCacheImpl implements BlameCache {
  public static CacheBuilder<Object, Object> newBuilder() {
    return CacheBuilder.newBuilder().maximumWeight(10 << 10);
  }

  public static class Key {
    private final ObjectId commitId;
    private final String path;
    private Repository repo;

    public Key(Repository repo, ObjectId commitId, String path) {
      this.commitId = commitId;
      this.path = path;
      this.repo = repo;
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
        return Objects.equal(commitId, k.commitId)
            && Objects.equal(path, k.path);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(commitId, path);
    }
  }

  private final LoadingCache<Key, List<BlameCache.Region>> cache;

  public BlameCacheImpl() {
    this(newBuilder());
  }

  public LoadingCache<?, ?> getCache() {
    return cache;
  }

  public BlameCacheImpl(CacheBuilder<Object, Object> builder) {
    this.cache = builder.weigher(new Weigher<Key, List<BlameCache.Region>>() {
      @Override
      public int weigh(Key key, List<BlameCache.Region> value) {
        return value.size();
      }
    }).build(new CacheLoader<Key, List<BlameCache.Region>>() {
      @Override
      public List<BlameCache.Region> load(Key key) throws IOException {
        return loadBlame(key);
      }
    });
  }

  @Override
  public List<BlameCache.Region> get(Repository repo, ObjectId commitId, String path)
      throws IOException {
    try {
      return cache.get(new Key(repo, commitId, path));
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  private List<BlameCache.Region> loadBlame(Key key) throws IOException {
    try {
      BlameGenerator gen = new BlameGenerator(key.repo, key.path);
      BlameResult blame;
      try {
        gen.push(null, key.commitId);
        blame = gen.computeBlameResult();
      } finally {
        gen.release();
      }
      if (blame == null) {
        return ImmutableList.of();
      }
      int lineCount = blame.getResultContents().size();
      blame.discardResultContents();

      List<BlameCache.Region> regions = Lists.newArrayList();
      for (int i = 0; i < lineCount; i++) {
        if (regions.isEmpty() || !regions.get(regions.size() - 1).growFrom(blame, i)) {
          regions.add(new BlameCache.Region(blame, i));
        }
      }
      return Collections.unmodifiableList(regions);
    } finally {
      key.repo = null;
    }
  }
}
