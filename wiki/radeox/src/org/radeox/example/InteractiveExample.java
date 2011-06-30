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

import org.radeox.EngineManager;
import org.radeox.engine.context.BaseRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.api.engine.RenderEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/*
 * Interactive example how to use EngineManager
 *
 * @author Stephan J. Schmidt
 * @version $Id: InteractiveExample.java,v 1.6 2003/10/07 08:20:24 stephan Exp $
 */

public class InteractiveExample {
  public static void main(String[] args) {
    System.err.println("Radeox 0.8");
    System.err.println("Copyright (c) 2003 Stephan J. Schmidt, Matthias L. Jugel. "
        + "\nAll Rights Reserved.");
    System.err.println("See License Agreement for terms and conditions of use.");

    RenderEngine engine = new BaseRenderEngine();

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    RenderContext context = new BaseRenderContext();
    String line;
    try {
      System.out.print("> ");
      System.out.flush();
      while ( (line = reader.readLine()) != null ) {
        System.out.println(engine.render(line, context));
        System.out.print("> ");
        System.out.flush();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
