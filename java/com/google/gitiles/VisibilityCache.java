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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.hash;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ExecutionError;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Cache of per-user object visibility. */
public class VisibilityCache {

  private static class Key {
    private final Object user;
    private final String repositoryName;
    private final ObjectId objectId;

    private Key(Object user, String repositoryName, ObjectId objectId) {
      this.user = checkNotNull(user, "user");
      this.repositoryName = checkNotNull(repositoryName, "repositoryName");
      this.objectId = checkNotNull(objectId, "objectId").copy();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Key) {
        Key k = (Key) o;
        return Objects.equals(user, k.user)
            && Objects.equals(repositoryName, k.repositoryName)
            && Objects.equals(objectId, k.objectId);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return hash(user, repositoryName, objectId);
    }

    @Override
    public String toString() {
      return toStringHelper(this)
          .add("user", user)
          .add("repositoryName", repositoryName)
          .add("objectId", objectId)
          .toString();
    }
  }

  private final Cache<Key, Boolean> cache;
  private final VisibilityChecker checker;

  public static CacheBuilder<Object, Object> defaultBuilder() {
    return CacheBuilder.newBuilder().maximumSize(1 << 10).expireAfterWrite(30, TimeUnit.MINUTES);
  }

  public VisibilityCache() {
    this(new VisibilityChecker(), defaultBuilder());
  }

  public VisibilityCache(CacheBuilder<Object, Object> builder) {
    this(new VisibilityChecker(), builder);
  }

  /**
   * Use the constructors with a boolean parameter (e.g. {@link #VisibilityCache()}). The default
   * visibility checker should cover all common use cases.
   *
   * <p>This constructor is useful to use a checker with additional logging or metrics collection,
   * for example.
   */
  public VisibilityCache(VisibilityChecker checker) {
    this(checker, defaultBuilder());
  }

  /**
   * Use the constructors with a boolean parameter (e.g. {@link #VisibilityCache()}). The default
   * visibility checker should cover all common use cases.
   *
   * <p>This constructor is useful to use a checker with additional logging or metrics collection,
   * for example.
   */
  public VisibilityCache(VisibilityChecker checker, CacheBuilder<Object, Object> builder) {
    this.cache = builder.build();
    this.checker = checker;
  }

  public Cache<?, Boolean> getCache() {
    return cache;
  }

  @VisibleForTesting
  boolean isVisible(
      final Repository repo,
      final RevWalk walk,
      GitilesAccess access,
      final ObjectId id,
      final ObjectId... knownReachable)
      throws IOException {
    try {
      return cache.get(
          new Key(access.getUserKey(), access.getRepositoryName(), id),
          () -> isVisible(repo, walk, id, Arrays.asList(knownReachable)));
    } catch (ExecutionException e) {
      Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
      throw new IOException(e);
    } catch (ExecutionError e) {
      // markUninteresting may overflow on pathological repos with very long merge chains. Play it
      // safe and return false rather than letting the error propagate.
      if (e.getCause() instanceof StackOverflowError) {
        return false;
      }
      throw e;
    }
  }

  boolean isVisible(Repository repo, RevWalk walk, ObjectId id, Collection<ObjectId> knownReachable)
      throws IOException {
    RevCommit commit;
    try {
      commit = walk.parseCommit(id);
    } catch (IncorrectObjectTypeException e) {
      return false;
    }

    RefDatabase refDb = repo.getRefDatabase();
    if (checker.isTipOfBranch(refDb, id)) {
      return true;
    }

    // Check heads first under the assumption that most requests are for refs close to a head. Tags
    // tend to be much further back in history and just clutter up the priority queue in the common
    // case.
    return checker.isReachableFrom("knownReachable", walk, commit, knownReachable)
        || isReachableFromRefs("heads", walk, commit, refDb.getRefsByPrefix(R_HEADS).stream())
        || isReachableFromRefs("tags", walk, commit, refDb.getRefsByPrefix(R_TAGS).stream())
        || isReachableFromRefs(
            "other", walk, commit, refDb.getRefs().stream().filter(VisibilityCache::otherRefs));
  }

  private static boolean refStartsWith(Ref ref, String prefix) {
    return ref.getName().startsWith(prefix);
  }

  private static boolean otherRefs(Ref r) {
    return !(refStartsWith(r, R_HEADS)
        || refStartsWith(r, R_TAGS)
        || refStartsWith(r, "refs/changes/"));
  }

  private boolean isReachableFromRefs(String desc, RevWalk walk, RevCommit commit, Stream<Ref> refs)
      throws IOException {
    return isReachableFrom(
        desc, walk, commit, refs.map(r -> firstNonNull(r.getPeeledObjectId(), r.getObjectId())));
  }

  private boolean isReachableFrom(String desc, RevWalk walk, RevCommit commit, Stream<ObjectId> ids)
      throws IOException {
    return checker.isReachableFrom(desc, walk, commit, ids.collect(toList()));
  }
}
