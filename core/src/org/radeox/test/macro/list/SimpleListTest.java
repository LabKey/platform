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
import org.junit.Test;
import org.radeox.macro.list.SimpleList;
import org.radeox.util.Linkable;
import org.radeox.util.Nameable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class SimpleListTest extends ListFormatterSupport {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    formatter = new SimpleList();
  }


  @Test
  public void testNameable() throws IOException
  {
    Collection<Nameable> c = Arrays.asList(new Nameable[]{
            () -> "name:test"
    });
    formatter.format(writer, emptyLinkable, "", c, "", false);

    Assert.assertEquals("Nameable is rendered",
        "<div class=\"list\"><div class=\"list-title\"></div><blockquote>name:test</blockquote></div>",
        writer.toString());
  }

  @Test
  public void testLinkable() throws IOException
  {
    Collection<Linkable> c = Arrays.asList(new Linkable[]{
            () -> "link:test"
    });
    formatter.format(writer, emptyLinkable, "", c, "", false);

    Assert.assertEquals("Linkable is rendered",
        "<div class=\"list\"><div class=\"list-title\"></div><blockquote>link:test</blockquote></div>",
        writer.toString());
  }

  @Test
  public void testSingeItem() throws IOException
  {
    Collection<String> c = Arrays.asList("test");
    formatter.format(writer,emptyLinkable, "", c, "", false);
    Assert.assertEquals("Single item is rendered",
        "<div class=\"list\"><div class=\"list-title\"></div><blockquote>test</blockquote></div>",
        writer.toString());
  }


  @Test
  public void testSize() throws IOException
  {
    Collection<String> c = Arrays.asList("test");
    formatter.format(writer, emptyLinkable, "", c, "", true);
    Assert.assertEquals("Size is rendered",
        "<div class=\"list\"><div class=\"list-title\"> (1)</div><blockquote>test</blockquote></div>",
        writer.toString());
  }

  @Test
  public void testEmpty() throws IOException
  {
    Collection<String> c = Arrays.asList(new String[]{});
    formatter.format(writer, emptyLinkable, "", c, "No items", false);
    Assert.assertEquals("Empty list is rendered",
        "<div class=\"list\"><div class=\"list-title\"></div>No items</div>",
        writer.toString());
  }

  @Test
  public void testTwoItems() throws IOException
  {
    Collection<String> c = Arrays.asList("test1", "test2");
    formatter.format(writer, emptyLinkable, "", c, "", false);
    Assert.assertEquals("Two items are rendered",
        "<div class=\"list\"><div class=\"list-title\"></div><blockquote>test1, test2</blockquote></div>",
        writer.toString());
  }

}
