// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public class MimeTypes {
  public static final String ANY = "application/octet-stream";
  private static final ImmutableMap<String, String> TYPES;

  static {
    Properties p = new Properties();
    try (InputStream in = MimeTypes.class.getResourceAsStream("mime-types.properties")) {
      p.load(in);
    } catch (IOException e) {
      throw new RuntimeException("Cannot load mime-types.properties", e);
    }

    ImmutableMap.Builder<String, String> m = ImmutableMap.builder();
    for (Map.Entry<Object, Object> e : p.entrySet()) {
      m.put(((String) e.getKey()).toLowerCase(), (String) e.getValue());
    }
    TYPES = m.build();
  }

  public static String getMimeType(String path) {
    int d = path.lastIndexOf('.');
    if (d == -1) {
      return ANY;
    }

    String ext = path.substring(d + 1);
    String type = TYPES.get(ext.toLowerCase());
    return MoreObjects.firstNonNull(type, ANY);
  }

  private MimeTypes() {}
}
