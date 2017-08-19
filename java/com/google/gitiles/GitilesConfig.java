// Copyright 2012 Google Inc. All Rights Reserved.
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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.servlet.FilterConfig;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

public class GitilesConfig {
  private static final String FILTER_CONFIG_PARAM = "configPath";
  private static final String PROPERTY_NAME = "com.google.gitiles.configPath";
  private static final String DEFAULT_PATH = "gitiles.config";

  public static File defaultFile() {
    return defaultFile(null);
  }

  public static File defaultFile(FilterConfig filterConfig) {
    String configPath = System.getProperty(PROPERTY_NAME, DEFAULT_PATH);
    if (configPath == null && filterConfig != null) {
      configPath = filterConfig.getInitParameter(FILTER_CONFIG_PARAM);
    }
    checkNotNull(configPath);
    return new File(configPath);
  }

  public static Config loadDefault() throws IOException, ConfigInvalidException {
    return loadDefault(null);
  }

  public static Config loadDefault(FilterConfig filterConfig)
      throws IOException, ConfigInvalidException {
    FileBasedConfig config = new FileBasedConfig(defaultFile(filterConfig), FS.DETECTED);
    config.load();
    return config;
  }

  public static void putVariant(
      Config config, String templateName, String keyName, Map<String, ? super String> out) {
    String variant = config.getString("template", null, templateName);
    if (variant != null) {
      out.put(keyName, variant);
    }
  }

  private GitilesConfig() {}
}
