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

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TimeCache}. */
@RunWith(JUnit4.class)
public class TimeCacheTest {
  private TestRepository<DfsRepository> repo;
  private RevWalk walk;
  private TimeCache cache;

  /**
   * Start time of {@link #repo}.
   *
   * <p>Note that commits auto-increment the repo's ticker, but tags do not.
   */
  private long start;

  @Before
  public void setUp() throws Exception {
    repo = new TestRepository<>(new InMemoryRepository(new DfsRepositoryDescription("test")));
    walk = new RevWalk(repo.getRepository());
    cache = new TimeCache();
    start = repo.getDate().getTime() / 1000;
  }

  private long getTime(ObjectId id) throws IOException {
    return cache.getTime(walk, id);
  }

  @Test
  public void commitTime() throws Exception {
    RevCommit root = repo.commit().create();
    RevCommit master = repo.commit().parent(root).create();
    assertThat(getTime(root)).isEqualTo(start + 1);
    assertThat(getTime(master)).isEqualTo(start + 2);
  }

  @Test
  public void taggedCommitTime() throws Exception {
    RevCommit commit = repo.commit().create();
    repo.tick(1);
    RevTag tag = repo.tag("tag", commit);
    assertThat(getTime(commit)).isEqualTo(start + 1);
    assertThat(getTime(tag)).isEqualTo(start + 2);
  }

  @Test
  public void taggedTreeAndBlobTime() throws Exception {
    RevBlob blob = repo.blob("contents");
    RevTree tree = repo.tree(repo.file("foo", blob));
    repo.tick(1);
    RevTag blobTag = repo.tag("blob", blob);
    repo.tick(1);
    RevTag treeTag = repo.tag("tree", tree);
    assertThat(getTime(blobTag)).isEqualTo(start + 1);
    assertThat(getTime(treeTag)).isEqualTo(start + 2);
  }

  @Test
  public void taggedTagTime() throws Exception {
    repo.tick(2);
    RevTag tag = repo.tag("tag", repo.commit().create());
    repo.tick(-1);
    RevTag tagTag = repo.tag("tagtag", tag);
    assertThat(getTime(tag)).isEqualTo(start + 3);
    assertThat(getTime(tagTag)).isEqualTo(start + 2);
  }

  @Test
  public void treeAndBlobTime() throws Exception {
    RevBlob blob = repo.blob("contents");
    RevTree tree = repo.tree(repo.file("foo", blob));
    assertThat(getTime(blob)).isEqualTo(Long.MIN_VALUE);
    assertThat(getTime(tree)).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void tagMissingTime() throws Exception {
    RevCommit commit = repo.commit().create();
    TagBuilder builder = new TagBuilder();
    builder.setObjectId(commit);
    builder.setTag("tag");
    builder.setMessage("");
    ObjectId id;
    try (ObjectInserter ins = repo.getRepository().newObjectInserter()) {
      id = ins.insert(builder);
      ins.flush();
    }
    assertThat(getTime(commit)).isEqualTo(start + 1);
    assertThat(getTime(id)).isEqualTo(start + 1);
  }

  @Test
  public void firstTagMissingTime() throws Exception {
    RevCommit commit = repo.commit().create();
    repo.tick(1);
    RevTag tag = repo.tag("tag", commit);
    repo.tick(1);

    TagBuilder builder = new TagBuilder();
    builder.setObjectId(tag);
    builder.setTag("tagtag");
    builder.setMessage("");
    ObjectId tagTagId;
    try (ObjectInserter ins = repo.getRepository().newObjectInserter()) {
      tagTagId = ins.insert(builder);
      ins.flush();
    }
    assertThat(getTime(commit)).isEqualTo(start + 1);
    assertThat(getTime(tag)).isEqualTo(start + 2);
    assertThat(getTime(tagTagId)).isEqualTo(start + 2);
  }
}
