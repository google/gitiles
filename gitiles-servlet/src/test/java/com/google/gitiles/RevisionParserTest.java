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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TAG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.common.cache.CacheBuilder;
import com.google.gitiles.RevisionParser.Result;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the revision parser. */
@RunWith(JUnit4.class)
public class RevisionParserTest {
  private TestRepository<DfsRepository> repo;
  private RevisionParser parser;

  @Before
  public void setUp() throws Exception {
    repo = new TestRepository<DfsRepository>(
        new InMemoryRepository(new DfsRepositoryDescription("test")));
    parser = new RevisionParser(
        repo.getRepository(),
        new TestGitilesAccess(repo.getRepository()).forRequest(null),
        new VisibilityCache(false, CacheBuilder.newBuilder().maximumSize(0)));
  }

  @Test
  public void parseRef() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    assertEquals(new Result(Revision.peeled("master", master)),
        parser.parse("master"));
    assertEquals(new Result(Revision.peeled("refs/heads/master", master)),
        parser.parse("refs/heads/master"));
    assertNull(parser.parse("refs//heads//master"));
    assertNull(parser.parse("refs heads master"));
  }

  @Test
  public void parseRefParentExpression() throws Exception {
    RevCommit root = repo.commit().create();
    RevCommit parent1 = repo.commit().parent(root).create();
    RevCommit parent2 = repo.commit().parent(root).create();
    RevCommit merge = repo.branch("master").commit()
        .parent(parent1)
        .parent(parent2)
        .create();
    assertEquals(new Result(Revision.peeled("master", merge)), parser.parse("master"));
    assertEquals(new Result(Revision.peeled("master^", parent1)), parser.parse("master^"));
    assertEquals(new Result(Revision.peeled("master~1", parent1)), parser.parse("master~1"));
    assertEquals(new Result(Revision.peeled("master^2", parent2)), parser.parse("master^2"));
    assertNull(parser.parse("master^3"));
    assertEquals(new Result(Revision.peeled("master~2", root)), parser.parse("master~2"));
  }

  @Test
  public void parseCommitShaVisibleFromHead() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.branch("master").commit().parent(parent).create();
    assertEquals(new Result(Revision.peeled(commit.name(), commit)), parser.parse(commit.name()));
    assertEquals(new Result(Revision.peeled(parent.name(), parent)), parser.parse(parent.name()));

    String abbrev = commit.name().substring(0, 6);
    assertEquals(new Result(Revision.peeled(abbrev, commit)), parser.parse(abbrev));
  }

  @Test
  public void parseCommitShaVisibleFromTag() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.commit().parent(parent).create();
    repo.branch("master").commit().create();
    repo.update("refs/tags/tag", repo.tag("tag", commit));

    assertEquals(new Result(Revision.peeled(commit.name(), commit)), parser.parse(commit.name()));
    assertEquals(new Result(Revision.peeled(parent.name(), parent)), parser.parse(parent.name()));
  }

  @Test
  public void parseCommitShaVisibleFromOther() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.commit().parent(parent).create();
    repo.branch("master").commit().create();
    repo.update("refs/tags/tag", repo.tag("tag", repo.commit().create()));
    repo.update("refs/meta/config", commit);

    assertEquals(new Result(Revision.peeled(commit.name(), commit)), parser.parse(commit.name()));
    assertEquals(new Result(Revision.peeled(parent.name(), parent)), parser.parse(parent.name()));
  }

  @Test
  public void parseCommitShaVisibleFromChange() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.commit().parent(parent).create();
    repo.branch("master").commit().create();
    repo.update("refs/changes/01/0001", commit);

    // Matches exactly.
    assertEquals(new Result(Revision.peeled(commit.name(), commit)), parser.parse(commit.name()));
    // refs/changes/* is excluded from ancestry search.
    assertEquals(null, parser.parse(parent.name()));
  }

  @Test
  public void parseNonVisibleCommitSha() throws Exception {
    RevCommit other = repo.commit().create();
    repo.branch("master").commit().create();
    assertEquals(null, parser.parse(other.name()));

    repo.branch("other").update(other);
    assertEquals(new Result(Revision.peeled(other.name(), other)), parser.parse(other.name()));
  }

  @Test
  public void parseDiffRevisions() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.branch("master").commit().parent(parent).create();
    RevCommit other = repo.branch("other").commit().create();

    assertEquals(
        new Result(
            Revision.peeled("master", commit),
            Revision.peeled("master^", parent),
            ""),
        parser.parse("master^..master"));
    assertEquals(
        new Result(
            Revision.peeled("master", commit),
            Revision.peeled("master^", parent),
            "/"),
        parser.parse("master^..master/"));
    assertEquals(
        new Result(
            Revision.peeled("master", commit),
            Revision.peeled("master^", parent),
            "/path/to/a/file"),
        parser.parse("master^..master/path/to/a/file"));
    assertEquals(
        new Result(
            Revision.peeled("master", commit),
            Revision.peeled("master^", parent),
            "/path/to/a/..file"),
        parser.parse("master^..master/path/to/a/..file"));
    assertEquals(
        new Result(
            Revision.peeled("refs/heads/master", commit),
            Revision.peeled("refs/heads/master^", parent),
            ""),
      parser.parse("refs/heads/master^..refs/heads/master"));
    assertEquals(
        new Result(
            Revision.peeled("master", commit),
            Revision.peeled("master~1", parent),
            ""),
        parser.parse("master~1..master"));
    // TODO(dborowitz): 2a2362fbb in JGit causes master~2 to resolve to master
    // rather than null. Uncomment when upstream regression is fixed.
    //assertNull(parser.parse("master~2..master"));
    assertEquals(
        new Result(
            Revision.peeled("master", commit),
            Revision.peeled("other", other),
            ""),
        parser.parse("other..master"));
  }

  @Test
  public void parseFirstParentExpression() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.branch("master").commit().parent(parent).create();

    assertEquals(
        new Result(
            Revision.peeled("master", commit),
            Revision.peeled("master^", parent),
            ""),
        parser.parse("master^!"));
    assertEquals(
        new Result(
            Revision.peeled("master^", parent),
            Revision.NULL,
            ""),
        parser.parse("master^^!"));
    assertEquals(
        new Result(
            Revision.peeled(parent.name(), parent),
            Revision.NULL,
            ""),
        parser.parse(parent.name() + "^!"));

    repo.update("refs/tags/tag", repo.tag("tag", commit));
    assertEquals(
        new Result(
            Revision.peeled("tag", commit),
            Revision.peeled("tag^", parent),
            ""),
        parser.parse("tag^!"));
    assertEquals(
        new Result(
            Revision.peeled("tag^", parent),
            Revision.NULL,
            ""),
        parser.parse("tag^^!"));
  }

  @Test
  public void nonVisibleDiffShas() throws Exception {
    RevCommit other = repo.commit().create();
    RevCommit master = repo.branch("master").commit().create();
    assertEquals(null, parser.parse("other..master"));
    assertEquals(null, parser.parse("master..other"));

    repo.branch("other").update(other);
    assertEquals(
        new Result(
            Revision.peeled("master", master),
            Revision.peeled("other", other),
            ""),
        parser.parse("other..master"));
    assertEquals(
        new Result(
            Revision.peeled("other", other),
            Revision.peeled("master", master),
            ""),
        parser.parse("master..other"));
  }

  @Test
  public void parseTag() throws Exception {
    RevCommit master = repo.branch("master").commit().create();
    RevTag masterTag = repo.update("refs/tags/master-tag", repo.tag("master-tag", master));
    RevTag masterTagTag = repo.update("refs/tags/master-tag-tag",
        repo.tag("master-tag-tag", master));

    assertEquals(new Result(
            new Revision("master-tag", masterTag, OBJ_TAG, master, OBJ_COMMIT)),
        parser.parse("master-tag"));
    assertEquals(new Result(
            new Revision("master-tag-tag", masterTagTag, OBJ_TAG, master, OBJ_COMMIT)),
        parser.parse("master-tag-tag"));

    RevBlob blob = repo.update("refs/tags/blob", repo.blob("blob"));
    RevTag blobTag = repo.update("refs/tags/blob-tag", repo.tag("blob-tag", blob));
    assertEquals(new Result(Revision.peeled("blob", blob)), parser.parse("blob"));
    assertEquals(new Result(new Revision("blob-tag", blobTag, OBJ_TAG, blob, OBJ_BLOB)),
        parser.parse("blob-tag"));
  }

  @Test
  public void parseUnsupportedRevisionExpressions() throws Exception {
    RevBlob blob = repo.blob("blob contents");
    RevCommit master = repo.branch("master").commit().add("blob", blob).create();

    assertEquals(master, repo.getRepository().resolve("master^{}"));
    assertEquals(null, parser.parse("master^{}"));

    assertEquals(master, repo.getRepository().resolve("master^{commit}"));
    assertEquals(null, parser.parse("master^{commit}"));

    assertEquals(blob, repo.getRepository().resolve("master:blob"));
    assertEquals(null, parser.parse("master:blob"));

    // TestRepository has no simple way of setting the reflog.
    //assertEquals(null, repo.getRepository().resolve("master@{0}"));
    assertEquals(null, parser.parse("master@{0}"));
  }

  @Test
  public void parseMissingSha() throws Exception {
    assertNull(parser.parse("deadbeef"));
    assertNull(parser.parse("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
  }
}
