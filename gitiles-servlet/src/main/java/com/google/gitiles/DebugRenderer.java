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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.tofu.SoyTofu;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/** Renderer that reloads Soy templates from the filesystem on every request. */
public class DebugRenderer extends Renderer {
  public DebugRenderer(String staticPrefix, String customTemplatesFilename,
      final String soyTemplatesRoot, String siteTitle) {
    super(
        new Function<String, URL>() {
          @Override
          public URL apply(String name) {
            return toFileURL(soyTemplatesRoot + File.separator + name);
          }
        },
        ImmutableMap.<String, String> of(), staticPrefix,
        toFileURL(customTemplatesFilename), siteTitle);
  }

  @Override
  protected SoyTofu getTofu() {
    SoyFileSet.Builder builder = new SoyFileSet.Builder()
        .setCompileTimeGlobals(globals);
    for (URL template : templates) {
      try {
        checkState(new File(template.toURI()).exists(), "Missing Soy template %s", template);
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e);
      }
      builder.add(template);
    }
    return builder.build().compileToTofu();
  }
}
