// Copyright (C) 2014 Google Inc. All Rights Reserved.
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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.TimeZone;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.SystemReader;

/** Date formatter similar in spirit to JGit's {@code GitDateFormatter}. */
public class DateFormatter {
  public enum Format {
    // Format strings should match org.eclipse.jgit.util.GitDateFormatter except
    // for the timezone suffix.
    DEFAULT("EEE MMM dd HH:mm:ss yyyy"),
    ISO("yyyy-MM-dd HH:mm:ss");

    private final String fmt;
    private final ThreadLocal<DateFormat> defaultFormat;
    private final ThreadLocal<DateFormat> fixedTzFormat;

    Format(String fmt) {
      this.fmt = fmt;
      this.defaultFormat = new ThreadLocal<>();
      this.fixedTzFormat = new ThreadLocal<>();
    }

    private DateFormat getDateFormat(Optional<TimeZone> fixedTz) {
      DateFormat df;
      if (fixedTz.isPresent()) {
        df = fixedTzFormat.get();
        if (df == null) {
          df = new SimpleDateFormat(fmt);
          fixedTzFormat.set(df);
        }
        df.setTimeZone(fixedTz.get());
      } else {
        df = defaultFormat.get();
        if (df == null) {
          df = new SimpleDateFormat(fmt + " Z");
          defaultFormat.set(df);
        }
      }
      return df;
    }
  }

  private final Optional<TimeZone> fixedTz;
  private final Format format;

  @VisibleForTesting
  protected DateFormatter(Optional<TimeZone> fixedTz, Format format) {
    this.fixedTz = fixedTz;
    this.format = format;
  }

  public DateFormatter(GitilesAccess access, Format format) throws IOException {
    this(ConfigUtil.getTimeZone(access.getConfig(), "gitiles", null, "fixedTimeZone"), format);
  }

  public String format(PersonIdent ident) {
    DateFormat df = format.getDateFormat(fixedTz);
    if (!fixedTz.isPresent()) {
      TimeZone tz = ident.getTimeZone();
      if (tz == null) {
        tz = SystemReader.getInstance().getTimeZone();
      }
      df.setTimeZone(tz);
    }
    return df.format(ident.getWhen());
  }
}
