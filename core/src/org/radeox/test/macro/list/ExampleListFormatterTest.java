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
package org.radeox.test.macro.list;

import org.junit.Assert;
import org.junit.Before;
import org.radeox.macro.list.ExampleListFormatter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class ExampleListFormatterTest extends ListFormatterSupport {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    formatter = new ExampleListFormatter();
  }

  @org.junit.Test
  public void testSize() throws IOException
  {
    Collection<String> c = Arrays.asList("test");
    formatter.format(writer, emptyLinkable, "", c, "", true);
    Assert.assertEquals("Size is rendered",
        "<div class=\"list\"><div class=\"list-title\"> (1)</div><ol><li>test</li></ol></div>",
        writer.toString());
  }

  @org.junit.Test
  public void testSingleItem() throws IOException
  {
    Collection<String> c = Arrays.asList("test");
    formatter.format(writer, emptyLinkable, "", c, "", false);
    Assert.assertEquals("Single item is rendered",
        "<div class=\"list\"><div class=\"list-title\"></div><ol><li>test</li></ol></div>",
        writer.toString());
  }
}
