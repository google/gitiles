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

import static com.google.gitiles.ConfigUtil.getDuration;
import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.lib.Config;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for configuration utilities. */
@RunWith(JUnit4.class)
public class ConfigUtilTest {
  @Test
  public void getDurationReturnsDuration() throws Exception {
    Duration def = Duration.standardSeconds(2);
    Config config = new Config();
    Duration t;

    config.setString("core", "dht", "timeout", "500 ms");
    t = getDuration(config, "core", "dht", "timeout", def);
    assertEquals(500, t.getMillis());

    config.setString("core", "dht", "timeout", "5.2 sec");
    t = getDuration(config, "core", "dht", "timeout", def);
    assertEquals(5200, t.getMillis());

    config.setString("core", "dht", "timeout", "1 min");
    t = getDuration(config, "core", "dht", "timeout", def);
    assertEquals(60000, t.getMillis());
  }
}
