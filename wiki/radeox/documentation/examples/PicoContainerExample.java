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
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.DefaultPicoContainer;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.engine.context.BaseRenderContext;
import org.radeox.engine.context.BaseInitialRenderContext;

import java.util.Locale;
import java.util.Enumeration;
import java.util.ResourceBundle;

/**
 * Example which shows howto use Radeox with PicoContainer
 *
 * @author Stephan J. Schmidt
 * @version $Id: PicoContainerExample.java,v 1.3 2004/02/06 07:46:17 stephan Exp $
 */

public class PicoContainerExample extends RadeoxTestSupport {
  public PicoContainerExample(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(PicoContainerExample.class);
  }

  public void testPicoContainer() {
// cut:start-1
    DefaultPicoContainer dc = new DefaultPicoContainer();
    try {
      // Register BaseRenderEngine as an Implementation
      // of RenderEngine
      dc.registerComponentImplementation(
          RenderEngine.class,
          BaseRenderEngine.class);
    } catch (Exception e) {
      System.err.println("Could not register component.");
    }

    // now only work with container
    PicoContainer container = dc;

    // Only ask for RenderEngine, we automatically
    // get an available object
    // that implements RenderEngine
    RenderEngine engine = (RenderEngine)
      container.getComponentInstance(RenderEngine.class);
    RenderContext context = new BaseRenderContext();
    String result = engine.render("__SnipSnap__", context);
// cut:end-1
    assertEquals("Rendered with PicoContainer.", "<b class=\"bold\">SnipSnap</b>", result);
  }

  public void testPicoWithInitialRenderContext() {
// cut:start-2
    DefaultPicoContainer dc = new DefaultPicoContainer();
    try {
      InitialRenderContext initialContext =
          new BaseInitialRenderContext();
      initialContext.set(RenderContext.OUTPUT_LOCALE,
                         new Locale("mywiki", "mywiki"));
      dc.registerComponentInstance(InitialRenderContext.class,
                                   initialContext);
      dc.registerComponentImplementation(RenderEngine.class,
                                         BaseRenderEngine.class);
    } catch (Exception e) {
      System.err.println("Could not register component.");
    }
// cut:end-2
    // now only work with container
    PicoContainer container = dc;

    // Only ask for RenderEngine, we automatically
    // get an available object
    // that implements RenderEngine
    RenderEngine engine = (RenderEngine)
      container.getComponentInstance(RenderEngine.class);

    assertNotNull("Component found.", engine);
    RenderContext context = new BaseRenderContext();
    String result = engine.render("__SnipSnap__", context);
    assertEquals("Rendered with PicoContainer and otherwiki Locale.",
                 "<b class=\"mybold\">SnipSnap</b>", result);

  }
}
