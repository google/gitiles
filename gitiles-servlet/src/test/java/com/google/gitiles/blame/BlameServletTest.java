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

package com.google.gitiles.blame;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Iterables;
import com.google.gitiles.CommitJsonData.Ident;
import com.google.gitiles.ServletTest;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class BlameServletTest extends ServletTest {
  private static class RegionJsonData {
    int start;
    int count;
    String path;
    String commit;
    Ident author;
  }

  @Test
  public void blameJson() throws Exception {
    String contents1 = "foo\n";
    String contents2 = "foo\ncontents\n";
    RevCommit c1 = repo.update("master", repo.commit().add("foo", contents1));
    RevCommit c2 = repo.update("master", repo.commit().tick(10).parent(c1).add("foo", contents2));

    Map<String, List<RegionJsonData>> result = getBlameJson("/repo/+blame/" + c2.name() + "/foo");
    assertEquals("regions", Iterables.getOnlyElement(result.keySet()));
    List<RegionJsonData> regions = result.get("regions");
    assertEquals(2, regions.size());

    RegionJsonData r1 = regions.get(0);
    assertEquals(1, r1.start);
    assertEquals(1, r1.count);
    assertEquals("foo", r1.path);
    assertEquals(c1.name(), r1.commit);
    assertEquals("J. Author", r1.author.name);
    assertEquals("jauthor@example.com", r1.author.email);
    assertEquals("2009-03-13 17:29:48 -0330", r1.author.time);

    RegionJsonData r2 = regions.get(1);
    assertEquals(2, r2.start);
    assertEquals(1, r2.count);
    assertEquals("foo", r2.path);
    assertEquals(c2.name(), r2.commit);
    assertEquals("J. Author", r2.author.name);
    assertEquals("jauthor@example.com", r2.author.email);
    assertEquals("2009-03-13 17:29:58 -0330", r2.author.time);
  }

  private Map<String, List<RegionJsonData>> getBlameJson(String path) throws Exception {
    return buildJson(path, new TypeToken<Map<String, List<RegionJsonData>>>() {}.getType());
  }
}
