package org.radeox.test.filter;

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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.filter.ListFilter;

public class ListFilterTest extends FilterTestSupport {
  public ListFilterTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    filter = new ListFilter();
//    context.getRenderContext().setRenderEngine((RenderEngine)
//        new MockWikiRenderEngine()
//    );
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(ListFilterTest.class);
  }

  public void testListsWithStrike() {
    assertEquals("<ul class=\"minus\">\n" +
                 "<li>test</li>\n" +
                 "<li>test</li>\n" +
                 "<li>test</li>\n" +
                 "</ul>", filter.filter("- test\n- test\n\n-----\n\n- test", context));
  }

  public void testUnnumberedListTwoItems() {
    assertEquals("<ul class=\"minus\">\n<li>test</li>\n<li>test</li>\n</ul>", filter.filter("- test\n- test", context));
  }

  public void testUnnumberedList() {
    assertEquals("<ul class=\"minus\">\n<li>test</li>\n</ul>", filter.filter("- test", context));
  }

  public void testOrderedList() {
    assertEquals("<ol>\n<li>test</li>\n<li>test</li>\n<li>test</li>\n</ol>",
                 filter.filter("1. test\n1. test\n 1. test", context));
  }

  public void testSimpleNestedList() {
    assertEquals("<ul class=\"minus\">\n" +
                 "<li>test</li>\n" +
                 "<ul class=\"minus\">\n" +
                 "<li>test</li>\n" +
                 "<li>test</li>\n" +
                 "</ul>\n" +
                 "<li>test</li>\n" +
                 "</ul>", filter.filter("- test\r\n-- test\r\n-- test\r\n- test", context));
  }

  public void testNestedList() {
    assertEquals("<ul class=\"minus\">\n" +
                 "<li>test</li>\n" +
                 "<ol class=\"alpha\">\n" +
                 "<li>test</li>\n" +
                 "<li>test</li>\n" +
                 "</ol>\n" +
                 "<li>test</li>\n" +
                 "</ul>", filter.filter("- test\n-a. test\n-a. test\n- test", context));
  }

  public void testSequentialLists() {
    assertEquals("<ul class=\"minus\">\n" +
                 "<li>test</li>\n" +
                 "</ul>TEXT\n" +
                 "<ul class=\"minus\">\n" +
                 "<li>test</li>\n" +
                 "</ul>", filter.filter("- test\nTEXT\n- test", context));
  }

  public void testListWithLinks() {
    assertEquals("<ul class=\"minus\">\n" +
                 "<li>[test]</li>\n" +
                 "<li>[test1]</li>\n" +
                 "<li>[test test2]</li>\n" +
                 "</ul>", filter.filter("- [test]\n- [test1]\n- [test test2]\n", context));
  }
}
