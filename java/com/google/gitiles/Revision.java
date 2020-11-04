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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.hash;
import static org.eclipse.jgit.lib.Constants.OBJ_BAD;
import static org.eclipse.jgit.lib.Constants.OBJ_TAG;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Objects;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Object encapsulating a single revision as seen by Gitiles.
 *
 * <p>A single revision consists of a name, an ID, and a type. Name parsing is done once per request
 * by {@link RevisionParser}.
 */
public class Revision {
  /** Sentinel indicating a missing or empty revision. */
  public static final Revision NULL = peeled("", ObjectId.zeroId(), OBJ_BAD);

  /** Common default branch given to clients. */
  public static final Revision HEAD = named("HEAD");

  public static Revision normalizeParentExpressions(Revision rev) {
    if (rev == null || (rev.name.indexOf('~') < 0 && rev.name.indexOf('^') < 0)) {
      return rev;
    }
    return new Revision(rev.id.name(), rev.id, rev.type, rev.peeledId, rev.peeledType);
  }

  private final String name;
  private final ObjectId id;
  private final int type;
  private final ObjectId peeledId;
  private final int peeledType;

  public static Revision peeled(String name, RevObject obj) {
    return peeled(name, obj, obj.getType());
  }

  public static Revision unpeeled(String name, ObjectId id) {
    return peeled(name, id, OBJ_BAD);
  }

  public static Revision named(String name) {
    return peeled(name, null, OBJ_BAD);
  }

  public static Revision peel(String name, RevObject obj, RevWalk walk)
      throws MissingObjectException, IOException {
    RevObject peeled = walk.peel(obj);
    return new Revision(name, obj, obj.getType(), peeled, peeled.getType());
  }

  private static Revision peeled(String name, ObjectId id, int type) {
    checkArgument(type != OBJ_TAG, "expected non-tag for %s/%s", name, id);
    return new Revision(name, id, type, id, type);
  }

  @VisibleForTesting
  Revision(String name, ObjectId id, int type, ObjectId peeledId, int peeledType) {
    this.name = name;
    this.id = id;
    this.type = type;
    this.peeledId = peeledId;
    this.peeledType = peeledType;
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isNull(Revision r) {
    return r == NULL;
  }

  public String getName() {
    return name;
  }

  public int getType() {
    return type;
  }

  public ObjectId getId() {
    return id;
  }

  public ObjectId getPeeledId() {
    return peeledId;
  }

  public int getPeeledType() {
    return peeledType;
  }

  public boolean matches(ObjectId other) {
    return id.equals(other) || nameEqualsAbbreviated(other);
  }

  public boolean nameIsId() {
    return nameEqualsAbbreviated(id);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Revision) {
      Revision r = (Revision) o;
      return Objects.equals(name, r.name)
          && Objects.equals(id, r.id)
          && type == r.type
          && Objects.equals(peeledId, r.peeledId)
          && peeledType == r.peeledType;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return hash(name, id, type, peeledId, peeledType);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .omitNullValues()
        .add("name", name)
        .add("id", id != null ? id.getName() : null)
        .add("type", type > 0 ? Constants.typeString(type) : null)
        .add("peeledId", peeledId != null ? peeledId.getName() : null)
        .add("peeledType", type > 0 ? Constants.typeString(peeledType) : null)
        .toString();
  }

  private boolean nameEqualsAbbreviated(ObjectId other) {
    return AbbreviatedObjectId.isId(name)
        && AbbreviatedObjectId.fromString(name).prefixCompare(other) == 0;
  }
}
