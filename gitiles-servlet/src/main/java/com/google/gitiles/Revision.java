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

import static com.google.common.base.Preconditions.checkArgument;
import static org.eclipse.jgit.lib.Constants.OBJ_BAD;
import static org.eclipse.jgit.lib.Constants.OBJ_TAG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * Object encapsulating a single revision as seen by Gitiles.
 * <p>
 * A single revision consists of a name, an ID, and a type. Name parsing is done
 * once per request by {@link RevisionParser}.
 */
public class Revision {
  /** Sentinel indicating a missing or empty revision. */
  public static final Revision NULL = peeled("", ObjectId.zeroId(), OBJ_BAD);

  /** Common default branch given to clients. */
  public static final Revision HEAD = named("HEAD");

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

  public boolean nameIsId() {
    return AbbreviatedObjectId.isId(name)
        && (AbbreviatedObjectId.fromString(name).prefixCompare(id) == 0);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Revision) {
      Revision r = (Revision) o;
      return Objects.equal(name, r.name)
          && Objects.equal(id, r.id)
          && Objects.equal(type, r.type)
          && Objects.equal(peeledId, r.peeledId)
          && Objects.equal(peeledType, r.peeledType);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, id, type, peeledId, peeledType);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .omitNullValues()
        .add("name", name)
        .add("id", id)
        .add("type", type)
        .add("peeledId", peeledId)
        .add("peeledType", peeledType)
        .toString();
  }
}
