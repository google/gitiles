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
import com.google.gitiles.FakeHttpServletRequest;
import com.google.gitiles.FakeHttpServletResponse;
import com.google.gitiles.GitilesServlet;
import com.google.gitiles.TestGitilesServlet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class BlameServletTest {
  private static class RegionJsonData {
    int start;
    int count;
    String path;
    String commit;
    Ident author;
  }

  private TestRepository<DfsRepository> repo;
  private GitilesServlet servlet;

  @Before
  public void setUp() throws Exception {
    DfsRepository r = new InMemoryRepository(new DfsRepositoryDescription("test"));
    repo = new TestRepository<DfsRepository>(r);
    servlet = TestGitilesServlet.create(repo);
  }

  @Test
  public void blameJson() throws Exception {
    String contents1 = "foo\n";
    String contents2 = "foo\ncontents\n";
    RevCommit c1 = repo.update("master", repo.commit().add("foo", contents1));
    RevCommit c2 = repo.update("master", repo.commit().tick(10).parent(c1).add("foo", contents2));

    Map<String, List<RegionJsonData>> result = getBlameJson("/test/+blame/" + c2.name() + "/foo");
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
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo(path);
    req.setQueryString("format=JSON");

    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);
    assertEquals(200, res.getStatus());
    String body = res.getActualBodyString();
    String magic = ")]}'\n";
    assertEquals(magic, body.substring(0, magic.length()));
    return new Gson().fromJson(body.substring(magic.length()),
        new TypeToken<Map<String, List<RegionJsonData>>>() {}.getType());
  }
}
