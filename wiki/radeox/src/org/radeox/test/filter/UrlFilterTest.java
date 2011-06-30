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
import org.radeox.filter.UrlFilter;

public class UrlFilterTest extends FilterTestSupport {
  public UrlFilterTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    filter = new UrlFilter();
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(UrlFilterTest.class);
  }

  public void testHttp() {
    assertEquals("<span class=\"nobr\"><a href=\"http://radeox.org\">&#104;ttp://radeox.org</a></span>",
                 filter.filter("http://radeox.org", context));
  }

  public void testHttps() {
    assertEquals("<span class=\"nobr\"><a href=\"https://radeox.org\">&#104;ttps://radeox.org</a></span>",
                 filter.filter("https://radeox.org", context));
  }

  public void testFtp() {
    assertEquals("<span class=\"nobr\"><a href=\"ftp://radeox.org\">&#102;tp://radeox.org</a></span>",
                 filter.filter("ftp://radeox.org", context));
  }
}
