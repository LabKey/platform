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

public class AllFilterTests extends TestCase {
  public AllFilterTests(String name) {
    super(name);
  }

  public static Test suite() {
    TestSuite s = new TestSuite();
    s.addTestSuite(BasicRegexTest.class);
    s.addTestSuite(ItalicFilterTest.class);
    s.addTestSuite(BoldFilterTest.class);
    s.addTestSuite(KeyFilterTest.class);
    s.addTestSuite(NewlineFilterTest.class);
    s.addTestSuite(LineFilterTest.class);
    s.addTestSuite(TypographyFilterTest.class);
    s.addTestSuite(HtmlRemoveFilterTest.class);
    s.addTestSuite(StrikeThroughFilterTest.class);
    s.addTestSuite(UrlFilterTest.class);
    s.addTestSuite(ParamFilterTest.class);
    s.addTestSuite(FilterPipeTest.class);
    s.addTestSuite(EscapeFilterTest.class);
    s.addTestSuite(InterWikiTest.class);
    s.addTestSuite(LinkTestFilterTest.class);
    s.addTestSuite(WikiLinkFilterTest.class);
    s.addTestSuite(SmileyFilterTest.class);
    s.addTestSuite(ListFilterTest.class);
    s.addTestSuite(HeadingFilterTest.class);
    return s;
  }

}
