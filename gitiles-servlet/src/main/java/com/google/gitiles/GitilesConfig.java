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

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;

import javax.servlet.FilterConfig;

public class GitilesConfig {
  private static final String FILTER_CONFIG_PARAM = "configPath";
  private static final String PROPERTY_NAME = "com.google.gitiles.configPath";
  private static final String DEFAULT_PATH = "gitiles.config";

  public static Config loadDefault() throws IOException, ConfigInvalidException {
    return loadDefault(null);
  }

  public static Config loadDefault(FilterConfig filterConfig)
      throws IOException, ConfigInvalidException {
    String configPath = null;
    if (filterConfig != null) {
      configPath = filterConfig.getInitParameter(FILTER_CONFIG_PARAM);
    }
    if (configPath == null) {
      configPath = System.getProperty(PROPERTY_NAME, DEFAULT_PATH);
    }
    FileBasedConfig config = new FileBasedConfig(new File(configPath), FS.DETECTED);
    config.load();
    return config;
  }

  private GitilesConfig() {}
}
