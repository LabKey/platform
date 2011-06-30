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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.radeox.EngineManager;
import org.radeox.test.filter.mock.MockWikiRenderEngine;
import org.radeox.api.engine.RenderEngine;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.engine.context.BaseRenderContext;
import org.radeox.api.engine.context.RenderContext;

import java.io.StringWriter;
import java.io.IOException;

public class BaseRenderEngineTest extends TestCase {
  RenderContext context;

  public BaseRenderEngineTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    context = new BaseRenderContext();
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(BaseRenderEngineTest.class);
  }

  public void testBoldInList() {
    RenderEngine engine = EngineManager.getInstance();
    assertEquals("<ul class=\"minus\">\n<li><b class=\"bold\">test</b></li>\n</ul>", engine.render("- __test__", context));
  }

  public void testRenderEngine() {
    String result = EngineManager.getInstance().render("__SnipSnap__ {link:Radeox|http://radeox.org}", context);
    assertEquals("<b class=\"bold\">SnipSnap</b> <span class=\"nobr\"><a href=\"http://radeox.org\">Radeox</a></span>", result);
  }

  public void testEmpty() {
    String result = EngineManager.getInstance().render("", context);
    assertEquals("", result);
  }

  public void testDefaultEngine() {
    RenderEngine engine = EngineManager.getInstance();
    RenderEngine engineDefault = EngineManager.getInstance(EngineManager.DEFAULT);
    assertEquals(engine.getName(), engineDefault.getName());
  }

  public void testWriter() {
    RenderEngine engine = new BaseRenderEngine();
    StringWriter writer = new StringWriter();
    try {
      engine.render(writer, "__SnipSnap__", context);
    } catch (IOException e) {
      // never reach
    }
    assertEquals("BaseRenderEngine writes to Writer","<b class=\"bold\">SnipSnap</b>", writer.toString());
  }

  public void testFilterOrder() {
    RenderEngine engine = EngineManager.getInstance();
    context.setRenderEngine(new MockWikiRenderEngine());
    assertEquals("'<link>' - '&#60;link&#62;'", engine.render("[<link>]", context));
  }
}
