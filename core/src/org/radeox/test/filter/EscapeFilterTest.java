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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.EscapeFilter;
import org.radeox.filter.Filter;
import org.radeox.filter.FilterPipe;
import org.radeox.filter.context.FilterContext;
import org.radeox.util.Encoder;

public class EscapeFilterTest extends FilterTestSupport {

  @Override
  @Before
  public void setUp() throws Exception {
    filter = new EscapeFilter();
    super.setUp();
  }

  @Test
  public void testEscapeH() {
    Assert.assertEquals("h is escaped", "&#104;", filter.filter("\\h", context));
  }

  @Test
  public void testBackslash() {
    // test "\\"
    Assert.assertEquals("\\\\ is kept escaped", "\\\\", filter.filter("\\\\", context));
  }

  @Test
  public void testBeforeEscape() {
    FilterPipe fp = new FilterPipe();
    Filter f = new Filter() {
      @Override
      public String[] replaces() {
        return new String[0];
      }

      @Override
      public void setInitialContext(InitialRenderContext context) {
      }

      @Override
      public String[] before() {
        return FilterPipe.EMPTY_BEFORE;
      }

      @Override
      public String filter(String input, FilterContext context) {
        return null;
      }

      @Override
      public String getDescription() {
        return "";
      }
    };

    fp.addFilter(f);
    fp.addFilter(filter);
    Assert.assertEquals("EscapeFilter is first", fp.getFilter(0), filter);
  }

  @Test
  public void testHTMLEncoderEscape() {
    Assert.assertEquals("&#60;link&#62;", Encoder.escape("<link>"));
  }

  @Test
  public void testHTMLEncoderUnescape() {
    Assert.assertEquals("<link>", Encoder.unescape("&#60;link&#62;"));
  }

  @Test
  public void testAmpersandEscape() {
    Assert.assertEquals("&#38;", filter.filter("&", context));
  }

}
