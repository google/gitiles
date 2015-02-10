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

package com.google.gitiles.doc;

import com.google.gitiles.GitilesView;
import com.google.gitiles.doc.html.HtmlBuilder;
import com.google.template.soy.shared.restricted.Sanitizers;

import org.pegdown.ast.HeaderNode;
import org.pegdown.ast.Node;
import org.pegdown.ast.ReferenceNode;
import org.pegdown.ast.RootNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class Navbar {
  static Map<String, Object> bannerSoyData(GitilesView view, RootNode nav) {
    Map<String, Object> data = new HashMap<>();
    data.put("siteTitle", null);
    data.put("logoUrl", null);
    data.put("homeUrl", null);

    if (nav == null) {
      return data;
    }

    for (Iterator<Node> i = nav.getChildren().iterator(); i.hasNext();) {
      Node n = i.next();
      if (n instanceof HeaderNode) {
        HeaderNode h = (HeaderNode) n;
        if (h.getLevel() == 1) {
          data.put("siteTitle", MarkdownHelper.getInnerText(h));
          i.remove();
          break;
        }
      }
    }

    for (ReferenceNode r : nav.getReferences()) {
      String key = MarkdownHelper.getInnerText(r);
      String url = r.getUrl();
      if ("logo".equalsIgnoreCase(key)) {
        Object src;
        if (HtmlBuilder.isImageDataUri(url)) {
          src = Sanitizers.filterImageDataUri(url);
        } else {
          src = url;
        }
        data.put("logoUrl", src);
      } else if ("home".equalsIgnoreCase(key)) {
        if (MarkdownHelper.isAbsolutePathToMarkdown(url)) {
          url = GitilesView.doc().copyFrom(view).setPathPart(url).toUrl();
        }
        data.put("homeUrl", url);
      }
    }
    return data;
  }

  private Navbar() {
  }
}
