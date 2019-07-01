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
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TAG;

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
    repo = new TestRepository<>(new InMemoryRepository(new DfsRepositoryDescription("test")));
    parser =
        new RevisionParser(
            repo.getRepository(),
            new TestGitilesAccess(repo.getRepository()).forRequest(null),
            new VisibilityCache(false, CacheBuilder.newBuilder().maximumSize(0)));
  }

  @Test
  public void parseRef() throws Exception {
    RevCommit master = repo.branch("refs/heads/master").commit().create();
    assertThat(parser.parse("master")).isEqualTo(new Result(Revision.peeled("master", master)));
    assertThat(parser.parse("refs/heads/master"))
        .isEqualTo(new Result(Revision.peeled("refs/heads/master", master)));
    assertThat(parser.parse("refs//heads//master")).isNull();
    assertThat(parser.parse("refs heads master")).isNull();
  }

  @Test
  public void parseRefParentExpression() throws Exception {
    RevCommit root = repo.commit().create();
    RevCommit parent1 = repo.commit().parent(root).create();
    RevCommit parent2 = repo.commit().parent(root).create();
    RevCommit merge = repo.branch("master").commit().parent(parent1).parent(parent2).create();
    assertThat(parser.parse("master")).isEqualTo(new Result(Revision.peeled("master", merge)));
    assertThat(parser.parse("master^")).isEqualTo(new Result(Revision.peeled("master^", parent1)));
    assertThat(parser.parse("master~1"))
        .isEqualTo(new Result(Revision.peeled("master~1", parent1)));
    assertThat(parser.parse("master^2"))
        .isEqualTo(new Result(Revision.peeled("master^2", parent2)));
    assertThat(parser.parse("master^3")).isNull();
    assertThat(parser.parse("master~2")).isEqualTo(new Result(Revision.peeled("master~2", root)));
  }

  @Test
  public void parseCommitShaVisibleFromHead() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.branch("master").commit().parent(parent).create();
    assertThat(parser.parse(commit.name()))
        .isEqualTo(new Result(Revision.peeled(commit.name(), commit)));
    assertThat(parser.parse(parent.name()))
        .isEqualTo(new Result(Revision.peeled(parent.name(), parent)));

    String abbrev = commit.name().substring(0, 6);
    assertThat(parser.parse(abbrev)).isEqualTo(new Result(Revision.peeled(abbrev, commit)));
  }

  @Test
  public void parseCommitShaVisibleFromTag() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.commit().parent(parent).create();
    repo.branch("master").commit().create();
    repo.update("refs/tags/tag", repo.tag("tag", commit));

    assertThat(parser.parse(commit.name()))
        .isEqualTo(new Result(Revision.peeled(commit.name(), commit)));
    assertThat(parser.parse(parent.name()))
        .isEqualTo(new Result(Revision.peeled(parent.name(), parent)));
  }

  @Test
  public void parseCommitShaVisibleFromOther() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.commit().parent(parent).create();
    repo.branch("master").commit().create();
    repo.update("refs/tags/tag", repo.tag("tag", repo.commit().create()));
    repo.update("refs/meta/config", commit);

    assertThat(parser.parse(commit.name()))
        .isEqualTo(new Result(Revision.peeled(commit.name(), commit)));
    assertThat(parser.parse(parent.name()))
        .isEqualTo(new Result(Revision.peeled(parent.name(), parent)));
  }

  @Test
  public void parseCommitShaVisibleFromChange() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.commit().parent(parent).create();
    repo.branch("master").commit().create();
    repo.update("refs/changes/01/0001", commit);

    // Matches exactly.
    assertThat(parser.parse(commit.name()))
        .isEqualTo(new Result(Revision.peeled(commit.name(), commit)));
    // refs/changes/* is excluded from ancestry search.
    assertThat(parser.parse(parent.name())).isNull();
  }

  @Test
  public void parseNonVisibleCommitSha() throws Exception {
    RevCommit other = repo.commit().create();
    repo.branch("master").commit().create();
    assertThat(parser.parse(other.name())).isNull();

    repo.branch("other").update(other);
    assertThat(parser.parse(other.name()))
        .isEqualTo(new Result(Revision.peeled(other.name(), other)));
  }

  @Test
  public void parseDiffRevisions() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.branch("master").commit().parent(parent).create();
    RevCommit other = repo.branch("other").commit().create();

    assertThat(parser.parse("master^..master"))
        .isEqualTo(
            new Result(Revision.peeled("master", commit), Revision.peeled("master^", parent), ""));
    assertThat(parser.parse("master^..master/"))
        .isEqualTo(
            new Result(Revision.peeled("master", commit), Revision.peeled("master^", parent), "/"));
    assertThat(parser.parse("master^..master/path/to/a/file"))
        .isEqualTo(
            new Result(
                Revision.peeled("master", commit),
                Revision.peeled("master^", parent),
                "/path/to/a/file"));
    assertThat(parser.parse("master^..master/path/to/a/..file"))
        .isEqualTo(
            new Result(
                Revision.peeled("master", commit),
                Revision.peeled("master^", parent),
                "/path/to/a/..file"));
    assertThat(parser.parse("refs/heads/master^..refs/heads/master"))
        .isEqualTo(
            new Result(
                Revision.peeled("refs/heads/master", commit),
                Revision.peeled("refs/heads/master^", parent),
                ""));
    assertThat(parser.parse("master~1..master"))
        .isEqualTo(
            new Result(Revision.peeled("master", commit), Revision.peeled("master~1", parent), ""));
    assertThat(parser.parse("master~2..master")).isNull();
    assertThat(parser.parse("other..master"))
        .isEqualTo(
            new Result(Revision.peeled("master", commit), Revision.peeled("other", other), ""));
  }

  @Test
  public void parseFirstParentExpression() throws Exception {
    RevCommit parent = repo.commit().create();
    RevCommit commit = repo.branch("master").commit().parent(parent).create();

    assertThat(parser.parse("master^!"))
        .isEqualTo(
            new Result(Revision.peeled("master", commit), Revision.peeled("master^", parent), ""));
    assertThat(parser.parse("master^^!"))
        .isEqualTo(new Result(Revision.peeled("master^", parent), Revision.NULL, ""));
    assertThat(parser.parse(parent.name() + "^!"))
        .isEqualTo(new Result(Revision.peeled(parent.name(), parent), Revision.NULL, ""));

    repo.update("refs/tags/tag", repo.tag("tag", commit));
    assertThat(parser.parse("tag^!"))
        .isEqualTo(new Result(Revision.peeled("tag", commit), Revision.peeled("tag^", parent), ""));
    assertThat(parser.parse("tag^^!"))
        .isEqualTo(new Result(Revision.peeled("tag^", parent), Revision.NULL, ""));
  }

  @Test
  public void nonVisibleDiffShas() throws Exception {
    RevCommit other = repo.commit().create();
    RevCommit master = repo.branch("master").commit().create();
    assertThat(parser.parse("other..master")).isNull();
    assertThat(parser.parse("master..other")).isNull();

    repo.branch("other").update(other);
    assertThat(parser.parse("other..master"))
        .isEqualTo(
            new Result(Revision.peeled("master", master), Revision.peeled("other", other), ""));
    assertThat(parser.parse("master..other"))
        .isEqualTo(
            new Result(Revision.peeled("other", other), Revision.peeled("master", master), ""));
  }

  @Test
  public void parseTag() throws Exception {
    RevCommit master = repo.branch("master").commit().create();
    RevTag masterTag = repo.update("refs/tags/master-tag", repo.tag("master-tag", master));
    RevTag masterTagTag =
        repo.update("refs/tags/master-tag-tag", repo.tag("master-tag-tag", master));

    assertThat(parser.parse("master-tag"))
        .isEqualTo(new Result(new Revision("master-tag", masterTag, OBJ_TAG, master, OBJ_COMMIT)));
    assertThat(parser.parse("master-tag-tag"))
        .isEqualTo(
            new Result(new Revision("master-tag-tag", masterTagTag, OBJ_TAG, master, OBJ_COMMIT)));

    RevBlob blob = repo.update("refs/tags/blob", repo.blob("blob"));
    RevTag blobTag = repo.update("refs/tags/blob-tag", repo.tag("blob-tag", blob));
    assertThat(parser.parse("blob")).isEqualTo(new Result(Revision.peeled("blob", blob)));
    assertThat(parser.parse("blob-tag"))
        .isEqualTo(new Result(new Revision("blob-tag", blobTag, OBJ_TAG, blob, OBJ_BLOB)));
  }

  @Test
  public void parseUnsupportedRevisionExpressions() throws Exception {
    RevBlob blob = repo.blob("blob contents");
    RevCommit master = repo.branch("master").commit().add("blob", blob).create();

    assertThat(repo.getRepository().resolve("master^{}")).isEqualTo(master);
    assertThat(parser.parse("master^{}")).isNull();

    assertThat(repo.getRepository().resolve("master^{commit}")).isEqualTo(master);
    assertThat(parser.parse("master^{commit}")).isNull();

    assertThat(repo.getRepository().resolve("master:blob")).isEqualTo(blob);
    assertThat(parser.parse("master:blob")).isNull();

    // InMemoryRepository doesn't implement a reflog, so we can't test reflog entries.
    // https://bugs.eclipse.org/bugs/show_bug.cgi?id=537972
    // assertThat(repo.getRepository().resolve("master@{0}")).isEqualTo(null);
    assertThat(parser.parse("master@{0}")).isNull();
  }

  @Test
  public void parseEmailInRevision() throws Exception {
    RevCommit c = repo.commit().create();
    RevCommit experimental = repo.update("refs/experimental/author@example.com/foo", c);
    assertThat(parser.parse("refs/experimental/author@example.com/foo"))
        .isEqualTo(new Result(Revision.peeled("refs/experimental/author@example.com/foo", c)));
  }

  @Test
  public void parseMissingSha() throws Exception {
    assertThat(parser.parse("deadbeef")).isNull();
    assertThat(parser.parse("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")).isNull();
  }
}
