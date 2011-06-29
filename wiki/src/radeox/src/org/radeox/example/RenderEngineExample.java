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

import org.radeox.engine.context.BaseRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.api.engine.RenderEngine;
import org.radeox.engine.BaseRenderEngine;

import java.util.Locale;

/*
 * Example how to use BaseRenderEngine
 *
 * @author Stephan J. Schmidt
 * @version $Id: RenderEngineExample.java,v 1.8 2003/10/07 08:20:24 stephan Exp $
 */

public class RenderEngineExample {
  public static void main(String[] args) {
    String test = "__SnipSnap__ {link:Radeox|http://radeox.org} ==Other Bold==";

    RenderContext context = new BaseRenderContext();
    RenderEngine engine = new BaseRenderEngine();
    System.out.println("Rendering with default:");
    System.out.println(engine.render(test, context));

    System.out.println("Rendering with alternative Wiki:");
    InitialRenderContext initialContext = new BaseInitialRenderContext();
    initialContext.set(RenderContext.INPUT_LOCALE, new Locale("otherwiki", ""));
    RenderEngine engineWithContext = new BaseRenderEngine(initialContext);
    System.out.println(engineWithContext.render(test, context));
  }
}
