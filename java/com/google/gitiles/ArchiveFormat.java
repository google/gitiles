// Copyright 2013 Google Inc. All Rights Reserved.
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

import com.google.common.base.Enums;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Optional;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.archive.TarFormat;
import org.eclipse.jgit.archive.Tbz2Format;
import org.eclipse.jgit.archive.TgzFormat;
import org.eclipse.jgit.archive.TxzFormat;
import org.eclipse.jgit.lib.Config;

public enum ArchiveFormat {
  TGZ("application/x-gzip", new TgzFormat()),
  TAR("application/x-tar", new TarFormat()),
  TBZ2("application/x-bzip2", new Tbz2Format()),
  TXZ("application/x-xz", new TxzFormat());
  // Zip is not supported because it may be interpreted by a Java plugin as a
  // valid JAR file, whose code would have access to cookies on the domain.

  private static final ImmutableMap<String, ArchiveFormat> BY_EXT;

  static {
    ImmutableMap.Builder<String, ArchiveFormat> byExt = ImmutableMap.builder();
    for (ArchiveFormat format : ArchiveFormat.values()) {
      for (String ext : format.getSuffixes()) {
        byExt.put(ext.toLowerCase(), format);
      }
    }
    BY_EXT = byExt.build();
  }

  /** Unregister all JGit archive formats supported by Gitiles. */
  public static void unregisterAll() {
    for (ArchiveFormat fmt : values()) {
      ArchiveCommand.unregisterFormat(fmt.getRegisteredName());
    }
  }

  @SuppressWarnings("ImmutableEnumChecker") // ArchiveCommand.Format is effectively immutable.
  private final ArchiveCommand.Format<?> format;

  private final String mimeType;

  ArchiveFormat(String mimeType, ArchiveCommand.Format<?> format) {
    this.format = format;
    this.mimeType = mimeType;
    ArchiveCommand.registerFormat(getRegisteredName(), format);
  }

  String getRegisteredName() {
    return getShortName();
  }

  String getShortName() {
    return name().toLowerCase();
  }

  String getMimeType() {
    return mimeType;
  }

  String getDefaultSuffix() {
    return getSuffixes().iterator().next();
  }

  Iterable<String> getSuffixes() {
    return format.suffixes();
  }

  static ArchiveFormat getDefault(Config cfg) {
    for (String allowed : cfg.getStringList("archive", null, "format")) {
      ArchiveFormat result =
          Enums.getIfPresent(ArchiveFormat.class, allowed.toUpperCase()).orNull();
      if (result != null) {
        return result;
      }
    }
    return TGZ;
  }

  static ImmutableSet<String> allExtensions() {
    return BY_EXT.keySet();
  }

  static Optional<ArchiveFormat> byExtension(String ext, Config cfg) {
    ArchiveFormat format = BY_EXT.get(ext);
    if (format == null) {
      return Optional.empty();
    }
    String[] formats = cfg.getStringList("archive", null, "format");
    if (formats.length == 0) {
      return Optional.of(format);
    }
    return Arrays.stream(formats)
        .filter(format.name()::equalsIgnoreCase)
        .findFirst()
        .map(x -> format);
  }
}
