// Copyright 2015 Google Inc. All Rights Reserved.
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

import com.google.gitiles.CommitJsonData.Commit;
import com.google.gitiles.CommitJsonData.Log;
import com.google.gitiles.DateFormatter.Format;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LogServlet}. */
@RunWith(JUnit4.class)
public class LogServletTest extends ServletTest {
  private static final TypeToken<Log> LOG = new TypeToken<Log>() {};

  @Test
  public void basicLog() throws Exception {
    RevCommit commit = repo.branch("HEAD").commit().create();

    Log response = buildJson(LOG, "/repo/+log");
    assertThat(response.log).hasSize(1);
    verifyJsonCommit(response.log.get(0), commit);
    assertThat(response.log.get(0).treeDiff).isNull();
  }

  @Test
  public void treeDiffLog() throws Exception {
    String contents1 = "foo\n";
    String contents2 = "foo\ncontents\n";
    RevCommit c1 = repo.update("master", repo.commit().add("foo", contents1));
    RevCommit c2 = repo.update("master", repo.commit().parent(c1).add("foo", contents2));

    Log response = buildJson(LOG, "/repo/+log/master", "name-status=1");
    assertThat(response.log).hasSize(2);

    Commit jc2 = response.log.get(0);
    verifyJsonCommit(jc2, c2);
    assertThat(jc2.treeDiff).hasSize(1);
    assertThat(jc2.treeDiff.get(0).type).isEqualTo("modify");
    assertThat(jc2.treeDiff.get(0).oldPath).isEqualTo("foo");
    assertThat(jc2.treeDiff.get(0).newPath).isEqualTo("foo");

    Commit jc1 = response.log.get(1);
    verifyJsonCommit(jc1, c1);
    assertThat(jc1.treeDiff).hasSize(1);
    assertThat(jc1.treeDiff.get(0).type).isEqualTo("add");
    assertThat(jc1.treeDiff.get(0).oldPath).isEqualTo("/dev/null");
    assertThat(jc1.treeDiff.get(0).newPath).isEqualTo("foo");
  }

  @Test
  public void firstParentLog() throws Exception {
    RevCommit p1 = repo.update("master", repo.commit().add("foo", "foo\n"));
    RevCommit p2 = repo.update("master", repo.commit().add("foo", "foo2\n"));
    RevCommit c = repo.update("master", repo.commit().parent(p1).parent(p2).add("foo", "foo3\n"));

    Log response = buildJson(LOG, "/repo/+log/master", "first-parent");
    assertThat(response.log).hasSize(2);

    verifyJsonCommit(response.log.get(0), c);
    verifyJsonCommit(response.log.get(1), p1);
  }

  @Test
  public void follow() throws Exception {
    String contents = "contents";
    RevCommit c1 = repo.branch("master").commit().add("foo", contents).create();
    RevCommit c2 = repo.branch("master").commit().rm("foo").add("bar", contents).create();
    repo.getRevWalk().parseBody(c1);
    repo.getRevWalk().parseBody(c2);

    Log response = buildJson(LOG, "/repo/+log/master/bar", "follow=0");
    assertThat(response.log).hasSize(1);
    verifyJsonCommit(response.log.get(0), c2);

    response = buildJson(LOG, "/repo/+log/master/bar");
    assertThat(response.log).hasSize(2);
    verifyJsonCommit(response.log.get(0), c2);
    verifyJsonCommit(response.log.get(1), c1);
  }

  private void verifyJsonCommit(Commit jsonCommit, RevCommit commit) throws Exception {
    repo.getRevWalk().parseBody(commit);
    GitilesAccess access = new TestGitilesAccess(repo.getRepository()).forRequest(null);
    DateFormatter df = new DateFormatter(access, Format.DEFAULT);
    assertThat(jsonCommit.commit).isEqualTo(commit.name());
    assertThat(jsonCommit.tree).isEqualTo(commit.getTree().name());

    ArrayList<String> expectedParents = new ArrayList<>();
    for (int i = 0; i < commit.getParentCount(); i++) {
      expectedParents.add(commit.getParent(i).name());
    }
    assertThat(jsonCommit.parents).containsExactlyElementsIn(expectedParents);

    assertThat(jsonCommit.author.name).isEqualTo(commit.getAuthorIdent().getName());
    assertThat(jsonCommit.author.email).isEqualTo(commit.getAuthorIdent().getEmailAddress());
    assertThat(jsonCommit.author.time).isEqualTo(df.format(commit.getAuthorIdent()));
    assertThat(jsonCommit.committer.name).isEqualTo(commit.getCommitterIdent().getName());
    assertThat(jsonCommit.committer.email).isEqualTo(commit.getCommitterIdent().getEmailAddress());
    assertThat(jsonCommit.committer.time).isEqualTo(df.format(commit.getCommitterIdent()));
    assertThat(jsonCommit.message).isEqualTo(commit.getFullMessage());
  }
}
