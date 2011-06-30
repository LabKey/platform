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

package examples;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.context.BaseRenderContext;

/**
 * Example which shows howto use Radeox with PicoContainer
 *
 * @author Stephan J. Schmidt
 * @version $Id: MyRenderEngineExample.java,v 1.2 2004/02/04 14:23:37 stephan Exp $
 */

public class MyRenderEngineExample extends RadeoxTestSupport {
  public MyRenderEngineExample(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(MyRenderEngineExample.class);
  }

  public void testWikiRenderEngine() {
    RenderContext context = new BaseRenderContext();
    RenderEngine engine = new MyWikiRenderEngine();
    context.setRenderEngine(engine);
    String result = engine.render("[stephan] and [leo]", context);
    assertEquals("Rendered wiki correctly.", "<a href=\"/show?wiki=stephan\">stephan</a> and leo<a href=\"/create?wiki=leo\">?</a>", result);
  }

  public void testRenderWithMyRenderEngine() {
// cut:start-1
    RenderContext context = new BaseRenderContext();
    RenderEngine engine = new MyRenderEngine();
    String result = engine.render("X and Y", context);
// cut:end-1
    assertEquals("Rendered correctly.", "Y and Y", result);

  }
}
