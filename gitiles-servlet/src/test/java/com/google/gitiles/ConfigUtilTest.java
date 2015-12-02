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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gitiles.ConfigUtil.getDuration;
import static com.google.gitiles.ConfigUtil.parseDuration;

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
    assertThat(t.getMillis()).isEqualTo(500);

    config.setString("core", "dht", "timeout", "5.2 sec");
    t = getDuration(config, "core", "dht", "timeout", def);
    assertThat(t.getMillis()).isEqualTo(5200);

    config.setString("core", "dht", "timeout", "1 min");
    t = getDuration(config, "core", "dht", "timeout", def);
    assertThat(t.getMillis()).isEqualTo(60000);
  }

  @Test
  public void parseDurationReturnsDuration() throws Exception {
    assertDoesNotParse(null);
    assertDoesNotParse("");
    assertDoesNotParse(" ");
    assertParses(500, "500 ms");
    assertParses(500, "500ms");
    assertParses(500, " 500 ms ");
    assertParses(5200, "5.2 sec");
    assertParses(60000, "1 min");
  }

  private static void assertDoesNotParse(String val) {
    assertThat(parseDuration(val)).named(String.valueOf(val)).isNull();
  }

  private static void assertParses(long expectedMillis, String val) {
    Duration actual = parseDuration(checkNotNull(val));
    assertThat(actual).named(val).isNotNull();
    assertThat(actual.getMillis()).named(val).isEqualTo(expectedMillis);
  }
}
