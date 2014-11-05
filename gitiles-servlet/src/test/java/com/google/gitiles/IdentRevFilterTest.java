// Copyright 2014 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link IdentRevFilter}.
 *
 * Unfortunately it's not easy to test the Filter using real {@link RevCommit}s
 * because {@link TestRepository} hard-codes its author as "J. Author". The next
 * best thing is to test a {@link PersonIdent}, those are easy to construct.
 * TODO(dborowitz): Fix TestRepository to allow this.
 */
@RunWith(JUnit4.class)
public class IdentRevFilterTest {
  @Test
  public void matchesName() throws Exception {
    IdentRevFilter filter = IdentRevFilter.author("eSt");
    assertTrue(filter.matchesPerson(new PersonIdent("eSt", "null@google.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("eStablish", "null@google.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("teSt", "null@google.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("teSting", "null@google.com")));
  }

  @Test
  public void caseSensitiveName() throws Exception {
    IdentRevFilter filter = IdentRevFilter.author("eSt");
    assertFalse(filter.matchesPerson(new PersonIdent("est", "null@google.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("Establish", "null@google.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("tESt", "null@google.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("tesTing", "null@google.com")));
  }

  @Test
  public void matchesEmailLocalPart() throws Exception {
    IdentRevFilter filter = IdentRevFilter.author("eSt");
    assertTrue(filter.matchesPerson(new PersonIdent("null", "eSt@google.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("null", "eStablish@google.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("null", "teSt@google.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("null", "teSting@google.com")));
  }

  @Test
  public void caseSensitiveEmailLocalPart() throws Exception {
    IdentRevFilter filter = IdentRevFilter.author("eSt");
    assertFalse(filter.matchesPerson(new PersonIdent("null", "est@google.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("null", "Establish@google.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("null", "tESt@google.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("null", "tesTing@google.com")));
  }

  @Test
  public void matchesEmailDomain() throws Exception {
    // git log --author matches the email domain as well as the enail name.
    IdentRevFilter filter = IdentRevFilter.author("eSt");
    assertTrue(filter.matchesPerson(new PersonIdent("null", "null@eSt.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("null", "null@eStablish.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("null", "null@teSt.com")));
    assertTrue(filter.matchesPerson(new PersonIdent("null", "null@teSting.com")));
  }

  @Test
  public void caseSensitiveEmailDomain() throws Exception {
    IdentRevFilter filter = IdentRevFilter.author("eSt");
    assertFalse(filter.matchesPerson(new PersonIdent("null", "null@est.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("null", "null@Establish.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("null", "null@tESt.com")));
    assertFalse(filter.matchesPerson(new PersonIdent("null", "null@tesTing.com")));
  }
}
