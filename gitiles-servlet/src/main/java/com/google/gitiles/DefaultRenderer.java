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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.tofu.SoyTofu;
import java.net.URL;
import java.util.Map;

/** Renderer that precompiles Soy and uses static precompiled CSS. */
public class DefaultRenderer extends Renderer {
  private final SoyTofu tofu;

  DefaultRenderer() {
    this("", ImmutableList.<URL>of(), "");
  }

  public DefaultRenderer(String staticPrefix, Iterable<URL> customTemplates, String siteTitle) {
    this(ImmutableMap.<String, String>of(), staticPrefix, customTemplates, siteTitle);
  }

  public DefaultRenderer(
      Map<String, String> globals,
      String staticPrefix,
      Iterable<URL> customTemplates,
      String siteTitle) {
    super(
        new Function<String, URL>() {
          @Override
          public URL apply(String name) {
            return Resources.getResource(Renderer.class, "templates/" + name);
          }
        },
        globals,
        staticPrefix,
        customTemplates,
        siteTitle);
    SoyFileSet.Builder builder = SoyFileSet.builder().setCompileTimeGlobals(this.globals);
    for (URL template : templates.values()) {
      builder.add(template);
    }
    tofu = builder.build().compileToTofu();
  }

  @Override
  protected SoyTofu getTofu() {
    return tofu;
  }
}
