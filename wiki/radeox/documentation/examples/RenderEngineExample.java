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
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.engine.context.BaseRenderContext;

import java.util.Locale;

/**
 * Example which shows howto use Radeox with PicoContainer
 *
 * @author Stephan J. Schmidt
 * @version $Id: RenderEngineExample.java,v 1.3 2004/02/03 14:51:49 stephan Exp $
 */

public class RenderEngineExample extends RadeoxTestSupport {

  public RenderEngineExample(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(RenderEngineExample.class);
  }

  public void testRender() {
// cut:start-2
    RenderContext context = new BaseRenderContext();
    RenderEngine engine = new BaseRenderEngine();
    String result = engine.render("__Radeox__", context);
// cut:end-2
    assertEquals("Rendered correctly.", "<b class=\"bold\">Radeox</b>", result);

  }

//  public String getName() {
//     return super.getName().substring(4).replaceAll("([A-Z])", " $1").toLowerCase();
//  }

  public void testRenderWithContext() {
// cut:start-1
    InitialRenderContext initialContext =
        new BaseInitialRenderContext();
    initialContext.set(RenderContext.INPUT_LOCALE,
                       new Locale("mywiki", "mywiki"));
    RenderEngine engineWithContext =
      new BaseRenderEngine(initialContext);
    String result = engineWithContext.render(
        "__Radeox__",
        new BaseRenderContext());
// cut:end-1
    assertEquals("Rendered with context.", "<b class=\"bold\">Radeox</b>", result);
  }
}
