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
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.EscapeFilter;
import org.radeox.filter.Filter;
import org.radeox.filter.FilterPipe;
import org.radeox.filter.context.FilterContext;
import org.radeox.util.Encoder;

public class EscapeFilterTest extends FilterTestSupport {
  public EscapeFilterTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    filter = new EscapeFilter();
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(EscapeFilterTest.class);
  }

  public void testEscapeH() {
    assertEquals("h is escaped", "&#104;", filter.filter("\\h", context));
  }

  public void testBackslash() {
    // test "\\"
    assertEquals("\\\\ is kept escaped", "\\\\", filter.filter("\\\\", context));
  }

  public void testBeforeEscape() {
    FilterPipe fp = new FilterPipe();
    Filter f = new Filter() {
      public String[] replaces() {
        return new String[0];
      }

      public void setInitialContext(InitialRenderContext context) {
      }

      public String[] before() {
        return FilterPipe.EMPTY_BEFORE;
      }

      public String filter(String input, FilterContext context) {
        return null;
      }

      public String getDescription() {
        return "";
      }
    };

    fp.addFilter(f);
    fp.addFilter(filter);
    assertEquals("EscapeFilter is first", fp.getFilter(0), filter);
  }

  public void testHTMLEncoderEscape() {
    assertEquals("&#60;link&#62;", Encoder.escape("<link>"));
  }

  public void testHTMLEncoderUnescape() {
    assertEquals("<link>", Encoder.unescape("&#60;link&#62;"));
  }

  public void testAmpersandEscape() {
    assertEquals("&#38;", filter.filter("&", context));
  }

}
