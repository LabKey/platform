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
import org.radeox.filter.TypographyFilter;

public class TypographyFilterTest extends FilterTestSupport {
  public TypographyFilterTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    filter = new TypographyFilter();
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(FilterTestSupport.class);
  }

  public void testElipsis() {
    assertEquals("Test &#8230; Text", filter.filter("Test ... Text", context));
  }

  public void testNotAfter() {
    assertEquals("...Text", filter.filter("...Text", context));
  }

  public void testEndOfLine() {
    assertEquals("Text&#8230;", filter.filter("Text...", context));
  }

  public void test4Dots() {
    assertEquals("Test .... Text", filter.filter("Test .... Text", context));
  }

  public void testLineStart() {
    assertEquals("&#8230; Text", filter.filter("... Text", context));
  }

  public void testLineEnd() {
    assertEquals("Test &#8230;", filter.filter("Test ...", context));
  }

}
