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
package org.radeox.test;

import org.junit.Before;
import org.radeox.EngineManager;
import org.radeox.test.filter.mock.MockWikiRenderEngine;
import org.radeox.api.engine.RenderEngine;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.engine.context.BaseRenderContext;
import org.radeox.api.engine.context.RenderContext;

import java.io.StringWriter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class BaseRenderEngineTest {
  RenderContext context;

  @Before
  public void setUp() throws Exception {
    context = new BaseRenderContext();
  }

  @org.junit.Test
  public void testBoldInList() {
    RenderEngine engine = EngineManager.getInstance();
    assertEquals("<ul class=\"minus\">\n<li><b class=\"bold\">test</b></li>\n</ul>", engine.render("- **test**", context));
  }

  @org.junit.Test
  public void testRenderEngine() {
    String result = EngineManager.getInstance().render("**SnipSnap** {link:Radeox|http://radeox.org}", context);
    assertEquals("<b class=\"bold\">SnipSnap</b> <span class=\"nobr\"><a href=\"http://radeox.org\">Radeox</a></span>", result);
  }

  @org.junit.Test
  public void testEmpty() {
    String result = EngineManager.getInstance().render("", context);
    assertEquals("", result);
  }

  @org.junit.Test
  public void testDefaultEngine() {
    RenderEngine engine = EngineManager.getInstance();
    RenderEngine engineDefault = EngineManager.getInstance(EngineManager.DEFAULT);
    assertEquals(engine.getName(), engineDefault.getName());
  }

  @org.junit.Test
  public void testWriter() throws IOException
  {
    RenderEngine engine = new BaseRenderEngine();
    StringWriter writer = new StringWriter();
    engine.render(writer, "**SnipSnap**", context);
    assertEquals("BaseRenderEngine writes to Writer","<b class=\"bold\">SnipSnap</b>", writer.toString());
  }

  @org.junit.Test
  public void testFilterOrder() {
    RenderEngine engine = EngineManager.getInstance();
    context.setRenderEngine(new MockWikiRenderEngine());
    assertEquals("'<link>' - '&#60;link&#62;'", engine.render("[<link>]", context));
  }
}
