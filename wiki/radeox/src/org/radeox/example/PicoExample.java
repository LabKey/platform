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

package org.radeox.example;

import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.DefaultPicoContainer;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.engine.context.BaseRenderContext;

import java.util.Locale;

/*
 * Example how to use BaseRenderEngine with Pico
 *
 * @author Stephan J. Schmidt
 * @version $Id: PicoExample.java,v 1.3 2003/12/16 10:26:51 leo Exp $
 */

public class PicoExample {
  public static void main(String[] args) {
    String test = "==SnipSnap== {link:Radeox|http://radeox.org}";

    DefaultPicoContainer c = new org.picocontainer.defaults.DefaultPicoContainer();
    try {
      InitialRenderContext initialContext = new BaseInitialRenderContext();
      initialContext.set(RenderContext.INPUT_LOCALE, new Locale("otherwiki", ""));
      c.registerComponentInstance(InitialRenderContext.class, initialContext);
      c.registerComponentImplementation(RenderEngine.class, BaseRenderEngine.class);
      c.getComponentInstances();
    } catch (Exception e) {
      System.err.println("Could not register component: "+e);
    }

    PicoContainer container = c;

    // no only work with container

    // Only ask for RenderEngine, we automatically get an object
    // that implements RenderEngine
    RenderEngine engine = (RenderEngine) container.getComponentInstance(RenderEngine.class);
    RenderContext context = new BaseRenderContext();
    System.out.println(engine.render(test, context));
   }
}
