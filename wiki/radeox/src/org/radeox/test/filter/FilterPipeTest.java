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
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.Filter;
import org.radeox.filter.FilterPipe;
import org.radeox.filter.context.FilterContext;
import org.radeox.test.filter.mock.MockReplacedFilter;
import org.radeox.test.filter.mock.MockReplacesFilter;

public class FilterPipeTest extends TestCase {
  public FilterPipeTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(FilterPipeTest.class);
  }

  public void testBefore() {
    FilterPipe fp = new FilterPipe();

    Filter f1 = new Filter() {
      public String[] before() {
        return FilterPipe.EMPTY_BEFORE;
      }

      public void setInitialContext(InitialRenderContext context) {
      }

      public String[] replaces() {
        return new String[0];  //To change body of implemented methods use Options | File Templates.
      }

      public String filter(String input, FilterContext context) {
        return null;
      }

      public String getDescription() {
        return "";
      }
    };
    // f2 should be inserted before f1
    Filter f2 = new Filter() {
      public String[] before() {
        return FilterPipe.FIRST_BEFORE;
      }

      public String[] replaces() {
        return new String[0];  //To change body of implemented methods use Options | File Templates.
      }

      public void setInitialContext(InitialRenderContext context) {
      }

      public String filter(String input, FilterContext context) {
        return null;
      }

      public String getDescription() {
        return "";
      }
    };
    fp.addFilter(f1);
    fp.addFilter(f2);
    assertEquals("'FIRST_BEFORE Filter is first in FilterPipe", fp.getFilter(0), f2 );
  }

  public void testReplace() {
    FilterPipe fp = new FilterPipe();

    Filter f1 = new MockReplacedFilter();
    Filter f2 = new MockReplacesFilter();

    fp.addFilter(f1);
    fp.addFilter(f2);

    fp.init();

    assertTrue("MockReplacedFilter is removed from FilterPipe", -1 == fp.index("org.radeox.test.filter.mock.MockReplacedFilter"));
    assertTrue("MockReplacesFilter is not removed from FilterPipe", -1 != fp.index("org.radeox.test.filter.mock.MockReplacesFilter"));
  }
}
