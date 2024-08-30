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

import org.junit.Before;
import org.radeox.filter.BoldFilter;

import static org.junit.Assert.assertEquals;

public class BoldFilterTest extends FilterTestSupport {

  @Override
  @Before
  public void setUp() throws Exception {
    filter = new BoldFilter();
    super.setUp();
  }

  @org.junit.Test
  public void testBold() {
    assertEquals("<b class=\"bold\">Text</b>", filter.filter("**Text**", context));
  }

  @org.junit.Test
  public void testBoldMustStartAndEndWithSpace() {
    assertEquals("Test__Text__Test", filter.filter("Test__Text__Test", context));
    assertEquals("Test**Text**Test", filter.filter("Test**Text**Test", context));
  }

  @org.junit.Test
  public void testBoldWithPunctuation() {
    assertEquals("<b class=\"bold\">Text</b>:", filter.filter("**Text**:", context));
  }
}
