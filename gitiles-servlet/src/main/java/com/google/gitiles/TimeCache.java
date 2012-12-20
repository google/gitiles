// Copyright 2012 Google Inc. All Rights Reserved.
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

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Cache of the time associated with Git objects.
 * <p>
 * Uses the time as stored in annotated tags if available, or else the commit
 * time of the tagged commit. Non-commits are given {@link Long#MIN_VALUE},
 * rather than searching for occurrences in the entire repository.
 */
public class TimeCache {
  public static CacheBuilder<Object, Object> newBuilder() {
    return CacheBuilder.newBuilder().maximumSize(10 << 10);
  }

  private final Cache<ObjectId, Long> cache;

  public TimeCache() {
    this(newBuilder());
  }

  public TimeCache(CacheBuilder<Object, Object> builder) {
    this.cache = builder.build();
  }

  public Cache<?, ?> getCache() {
    return cache;
  }

  Long getTime(final RevWalk walk, final ObjectId id) throws IOException {
    try {
      return cache.get(id, new Callable<Long>() {
        @Override
        public Long call() throws IOException {
          RevObject o = walk.parseAny(id);
          while (o instanceof RevTag) {
            RevTag tag = (RevTag) o;
            PersonIdent ident = tag.getTaggerIdent();
            if (ident != null) {
              return ident.getWhen().getTime() / 1000;
            }
            o = tag.getObject();
            walk.parseHeaders(o);
          }
          if (o.getType() == Constants.OBJ_COMMIT) {
            return Long.valueOf(((RevCommit) o).getCommitTime());
          }
          return Long.MIN_VALUE;
        }
      });
    } catch (ExecutionException e) {
      Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
      throw new IOException(e);
    }
  }
}
