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

import com.google.common.base.Predicates;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.eclipse.jgit.lib.Config;
import org.joda.time.Duration;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilities for working with {@link Config} objects. */
public class ConfigUtil {
  /**
   * Read a duration value from the configuration.
   * <p>
   * Durations can be written as expressions, for example {@code "1 s"} or
   * {@code "5 days"}. If units are not specified, milliseconds are assumed.
   *
   * @param config JGit config object.
   * @param section section to read, e.g. "google"
   * @param subsection subsection to read, e.g. "bigtable"
   * @param name variable to read, e.g. "deadline".
   * @param defaultValue value to use when the value is not assigned.
   * @return a standard duration representing the time read, or defaultValue.
   */
  public static Duration getDuration(Config config, String section, String subsection, String name,
      Duration defaultValue) {
    String valStr = config.getString(section, subsection, name);
    if (valStr == null) {
        return defaultValue;
    }

    valStr = valStr.trim();
    if (valStr.length() == 0) {
        return defaultValue;
    }

    Matcher m = matcher("^([1-9][0-9]*(?:\\.[0-9]*)?)\\s*(.*)$", valStr);
    if (!m.matches()) {
      String key = section + (subsection != null ? "." + subsection : "") + "." + name;
      throw new IllegalStateException("Not time unit: " + key + " = " + valStr);
    }

    String digits = m.group(1);
    String unitName = m.group(2).trim();

    TimeUnit unit;
    if ("".equals(unitName)) {
      unit = TimeUnit.MILLISECONDS;
    } else if (anyOf(unitName, "ms", "millis", "millisecond", "milliseconds")) {
      unit = TimeUnit.MILLISECONDS;
    } else if (anyOf(unitName, "s", "sec", "second", "seconds")) {
      unit = TimeUnit.SECONDS;
    } else if (anyOf(unitName, "m", "min", "minute", "minutes")) {
      unit = TimeUnit.MINUTES;
    } else if (anyOf(unitName, "h", "hr", "hour", "hours")) {
      unit = TimeUnit.HOURS;
    } else if (anyOf(unitName, "d", "day", "days")) {
      unit = TimeUnit.DAYS;
    } else {
      String key = section + (subsection != null ? "." + subsection : "") + "." + name;
      throw new IllegalStateException("Not time unit: " + key + " = " + valStr);
    }

    try {
      if (digits.indexOf('.') == -1) {
        long val = Long.parseLong(digits);
        return new Duration(val * TimeUnit.MILLISECONDS.convert(1, unit));
      } else {
        double val = Double.parseDouble(digits);
        return new Duration((long) (val * TimeUnit.MILLISECONDS.convert(1, unit)));
      }
    } catch (NumberFormatException nfe) {
      String key = section + (subsection != null ? "." + subsection : "") + "." + name;
      throw new IllegalStateException("Not time unit: " + key + " = " + valStr, nfe);
    }
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
      if (config.getString("cache", name, "maximumWeight") != null) {
        b.maximumWeight(config.getLong("cache", name, "maximumWeight", 20 << 20));
      }
      if (config.getString("cache", name, "maximumSize") != null) {
        b.maximumSize(config.getLong("cache", name, "maximumSize", 16384));
      }
      Duration expireAfterWrite = getDuration(config, "cache", name, "expireAfterWrite", null);
      if (expireAfterWrite != null) {
        b.expireAfterWrite(expireAfterWrite.getMillis(), TimeUnit.MILLISECONDS);
      }
      Duration expireAfterAccess = getDuration(config, "cache", name, "expireAfterAccess", null);
      if (expireAfterAccess != null) {
        b.expireAfterAccess(expireAfterAccess.getMillis(), TimeUnit.MILLISECONDS);
      }
      // Add other methods as needed.
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Error getting CacheBuilder for " + name, e);
    } catch (IllegalStateException e) {
      throw new IllegalStateException("Error getting CacheBuilder for " + name, e);
    }
    return b;
  }

  private static Matcher matcher(String pattern, String valStr) {
      return Pattern.compile(pattern).matcher(valStr);
  }

  private static boolean anyOf(String a, String... cases) {
    return Iterables.any(ImmutableList.copyOf(cases),
        Predicates.equalTo(a.toLowerCase()));
  }

  private ConfigUtil() {
  }
}
