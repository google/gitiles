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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.eclipse.jgit.lib.Config;

/** Utilities for working with {@link Config} objects. */
public class ConfigUtil {
  /**
   * Read a duration value from the configuration.
   *
   * <p>Durations can be written with unit suffixes, for example {@code "1 s"} or {@code "5 days"}.
   * If units are not specified, milliseconds are assumed.
   *
   * @param config JGit config object.
   * @param section section to read, e.g. "google"
   * @param subsection subsection to read, e.g. "bigtable"
   * @param name variable to read, e.g. "deadline".
   * @param defaultValue value to use when the value is not assigned.
   * @return a standard duration representing the time read, or defaultValue.
   */
  @Nullable
  public static Duration getDuration(
      Config config,
      String section,
      String subsection,
      String name,
      @Nullable Duration defaultValue) {
    long m = config.getTimeUnit(section, subsection, name, -1, MILLISECONDS);
    return m == -1 ? defaultValue : Duration.ofMillis(m);
  }

  /**
   * Get a {@link CacheBuilder} from a config.
   *
   * @param config JGit config object.
   * @param name name of the cache subsection under the "cache" section.
   * @return a new cache builder.
   */
  public static CacheBuilder<Object, Object> getCacheBuilder(Config config, String name) {
    CacheBuilder<Object, Object> b = CacheBuilder.newBuilder();
    try {
      if (config.getString("cache", name, "concurrencyLevel") != null) {
        b.concurrencyLevel(config.getInt("cache", name, "concurrencyLevel", 4));
      }
      if (config.getString("cache", name, "maximumWeight") != null) {
        b.maximumWeight(config.getLong("cache", name, "maximumWeight", 20 << 20));
      }
      if (config.getString("cache", name, "maximumSize") != null) {
        b.maximumSize(config.getLong("cache", name, "maximumSize", 16384));
      }
      Duration expireAfterWrite = getDuration(config, "cache", name, "expireAfterWrite", null);
      if (expireAfterWrite != null) {
        b.expireAfterWrite(expireAfterWrite.toMillis(), TimeUnit.MILLISECONDS);
      }
      Duration expireAfterAccess = getDuration(config, "cache", name, "expireAfterAccess", null);
      if (expireAfterAccess != null) {
        b.expireAfterAccess(expireAfterAccess.toMillis(), TimeUnit.MILLISECONDS);
      }
      // Add other methods as needed.
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Error getting CacheBuilder for " + name, e);
    } catch (IllegalStateException e) {
      throw new IllegalStateException("Error getting CacheBuilder for " + name, e);
    }
    return b;
  }

  /**
   * Get a {@link TimeZone} from a config.
   *
   * @param config JGit config object.
   * @param section section to read, e.g. "gitiles".
   * @param subsection subsection to read, e.g. "subsection".
   * @param name variable to read, e.g. "fixedTimeZone".
   * @return a time zone read from parsing the specified config string value, or {@link
   *     Optional#empty()} if not present. As in the behavior of {@link
   *     TimeZone#getTimeZone(String)}, unknown time zones are treated as GMT.
   */
  public static Optional<TimeZone> getTimeZone(
      Config config, String section, String subsection, String name) {
    return Optional.ofNullable(config.getString(section, subsection, name))
        .map(TimeZone::getTimeZone);
  }

  private ConfigUtil() {}
}
