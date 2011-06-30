/*
 * This file is part of "SnipSnap Radeox Rendering Engine".
 *
 * Copyright (c) 2002 Stephan J. Schmidt, Matthias L. Jugel
 * All Rights Reserved.
 *
 * Please visit http://radeox.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * --LICENSE NOTICE--
 */
package org.radeox.test.filter;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.filter.StrikeThroughFilter;

public class StrikeThroughFilterTest extends FilterTestSupport {
  public StrikeThroughFilterTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    filter = new StrikeThroughFilter();
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(StrikeThroughFilterTest.class);
  }

  public void testStrikeThroughDash() {
    assertEquals("Test<strike class=\"strike\">Test-Text</strike>", filter.filter("Test--Test-Text--", context));
  }

  public void testStrikeThroughDoubleDash() {
    assertEquals("Test<strike class=\"strike\">Test</strike>Text--", filter.filter("Test--Test--Text--", context));
  }

  public void testStartStrikeThrough() {
    assertEquals("Test<strike class=\"strike\">Text</strike>", filter.filter("Test--Text--", context));
  }

  public void testEndStrikeThrough() {
    assertEquals("<strike class=\"strike\">Text</strike>Test", filter.filter("--Text--Test", context));
  }

  public void testStrikeThrough() {
    assertEquals("Test<strike class=\"strike\">Text</strike>Test", filter.filter("Test--Text--Test", context));
  }

  public void testFourDashes() {
    assertEquals("----", filter.filter("----",context));
  }

  public void testFiveDashes() {
    assertEquals("-----", filter.filter("-----", context));
  }

  public void testHtmlComment() {
    assertEquals("<!-- comment -->", filter.filter("<!-- comment -->", context));
  }

}
