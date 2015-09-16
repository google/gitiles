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

package com.google.gitiles;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DiffServletTest extends ServletTest {
  @Test
  public void diffFileOneParentHtml() throws Exception {
    String contents1 = "foo\n";
    String contents2 = "foo\ncontents\n";
    RevCommit c1 = repo.update("master", repo.commit().add("foo", contents1));
    RevCommit c2 = repo.update("master", repo.commit().parent(c1).add("foo", contents2));

    String actual = buildHtml("/repo/+diff/" + c2.name() + "^!/foo", false);

    String diffHeader = String.format(
        "diff --git <a href=\"/b/repo/+/%s/foo\">a/foo</a> <a href=\"/b/repo/+/%s/foo\">b/foo</a>",
        c1.name(), c2.name());
    assertThat(actual).contains(diffHeader);
  }

  @Test
  public void diffFileNoParentsText() throws Exception {
    String contents = "foo\ncontents\n";
    RevCommit c = repo.update("master", repo.commit().add("foo", contents));

    FakeHttpServletResponse res = buildText("/repo/+diff/" + c.name() + "^!/foo");

    Patch p = parsePatch(res.getActualBody());
    FileHeader f = getOnlyElement(p.getFiles());
    assertThat(f.getChangeType()).isEqualTo(ChangeType.ADD);
    assertThat(f.getPath(Side.OLD)).isEqualTo(DiffEntry.DEV_NULL);
    assertThat(f.getPath(Side.NEW)).isEqualTo("foo");

    RawText rt = new RawText(contents.getBytes(UTF_8));
    Edit e = getOnlyElement(getOnlyElement(f.getHunks()).toEditList());
    assertThat(e.getType()).isEqualTo(Type.INSERT);
    assertThat(rt.getString(e.getBeginB(), e.getEndB(), false)).isEqualTo(contents);
  }

  @Test
  public void diffFileOneParentText() throws Exception {
    String contents1 = "foo\n";
    String contents2 = "foo\ncontents\n";
    RevCommit c1 = repo.update("master", repo.commit().add("foo", contents1));
    RevCommit c2 = repo.update("master", repo.commit().parent(c1).add("foo", contents2));

    FakeHttpServletResponse res = buildText("/repo/+diff/" + c2.name() + "^!/foo");

    Patch p = parsePatch(res.getActualBody());
    FileHeader f = getOnlyElement(p.getFiles());
    assertThat(f.getChangeType()).isEqualTo(ChangeType.MODIFY);
    assertThat(f.getPath(Side.OLD)).isEqualTo("foo");
    assertThat(f.getPath(Side.NEW)).isEqualTo("foo");

    RawText rt2 = new RawText(contents2.getBytes(UTF_8));
    Edit e = getOnlyElement(getOnlyElement(f.getHunks()).toEditList());
    assertThat(e.getType()).isEqualTo(Type.INSERT);
    assertThat(rt2.getString(e.getBeginB(), e.getEndB(), false)).isEqualTo("contents\n");
  }

  @Test
  public void diffDirectoryText() throws Exception {
    String contents = "contents\n";
    RevCommit c = repo.update("master", repo.commit()
        .add("dir/foo", contents)
        .add("dir/bar", contents)
        .add("baz", contents));

    FakeHttpServletResponse res = buildText("/repo/+diff/" + c.name() + "^!/dir");

    Patch p = parsePatch(res.getActualBody());
    assertThat(p.getFiles().size()).isEqualTo(2);
    assertThat(p.getFiles().get(0).getPath(Side.NEW)).isEqualTo("dir/bar");
    assertThat(p.getFiles().get(1).getPath(Side.NEW)).isEqualTo("dir/foo");
  }

  private static Patch parsePatch(byte[] enc) {
    byte[] buf = BaseEncoding.base64().decode(new String(enc, UTF_8));
    Patch p = new Patch();
    p.parse(buf, 0, buf.length);
    assertThat(p.getErrors()).isEqualTo(ImmutableList.of());
    return p;
  }
}
