// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.gitiles;

/** Assertion methods for Gitiles. */
public class MoreAssert {
  private MoreAssert() {}

  /** Simple version of assertThrows that will be introduced in JUnit 4.13. */
  public static <T extends Throwable> T assertThrows(Class<T> expected, ThrowingRunnable r) {
    try {
      r.run();
      throw new AssertionError("Expected " + expected.getSimpleName() + " to be thrown");
    } catch (Throwable actual) {
      if (expected.isAssignableFrom(actual.getClass())) {
        return (T) actual;
      }
      throw new AssertionError(
          "Expected " + expected.getSimpleName() + ", but got " + actual.getClass().getSimpleName(),
          actual);
    }
  }

  public interface ThrowingRunnable {
    void run() throws Throwable;
  }
}
