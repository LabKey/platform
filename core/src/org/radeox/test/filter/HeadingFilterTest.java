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
import org.junit.Test;
import org.radeox.filter.HeadingFilter;

import static org.junit.Assert.assertEquals;

public class HeadingFilterTest extends FilterTestSupport {
  @Override
  @Before
  public void setUp() throws Exception {
    filter = new HeadingFilter();
    super.setUp();
  }

  @Test
  public void testHeading() {
    assertEquals("<h3 class=\"heading-1\">Test</h3>", filter.filter("1 Test", context));
  }

  @Test
  public void testSubHeadings() {
    assertEquals("""
            <h3 class="heading-1">Test</h3>
            <h3 class="heading-1-1">Test</h3>
            <h3 class="heading-1-1-1">Test</h3>
            <h3 class="heading-1">Test</h3>""", filter.filter("1 Test\n1.1 Test\n1.1.1 Test\n1 Test", context));
  }
}
