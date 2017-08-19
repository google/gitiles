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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gitiles.DateFormatter;
import com.google.gitiles.blame.cache.Region;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;

class RegionAdapter extends TypeAdapter<Region> {
  private final DateFormatter df;

  RegionAdapter(DateFormatter df) {
    this.df = checkNotNull(df, "DateFormatter");
  }

  @Override
  public void write(JsonWriter out, Region value) throws IOException {
    out.beginObject()
        .name("start")
        .value(value.getStart() + 1)
        .name("count")
        .value(value.getCount())
        .name("path")
        .value(value.getSourcePath())
        .name("commit")
        .value(ObjectId.toString(value.getSourceCommit()))
        .name("author")
        .beginObject()
        // TODO(dborowitz): Use an adapter from CommitJsonData instead.
        .name("name")
        .value(value.getSourceAuthor().getName())
        .name("email")
        .value(value.getSourceAuthor().getEmailAddress())
        .name("time")
        .value(df.format(value.getSourceAuthor()))
        .endObject()
        .endObject();
  }

  @Override
  public Region read(JsonReader in) throws IOException {
    throw new UnsupportedOperationException();
  }
}
