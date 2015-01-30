/*
 * This file is part of "SnipSnap Wiki/Weblog".
 *
 * Copyright (c) 2002 Stephan J. Schmidt, Matthias L. Jugel
 * All Rights Reserved.
 *
 * Please visit http://snipsnap.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * --LICENSE NOTICE--
 */
package org.radeox.test;

import org.labkey.api.util.StringUtilsLabKey;
import org.radeox.EngineManager;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.context.BaseRenderContext;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class RegexpTest {
  public static void main(String[] args) {
    //System.out.print("Press enter ...");
//    try {
//      new BufferedReader(new InputStreamReader(System.in)).readLine();
//    } catch (IOException e) {
//      // ignore errors
//    }

    String file = args.length > 0 ? args[0] : "conf/wiki.txt";
    try {
      System.setOut(new PrintStream(System.out, true, StringUtilsLabKey.DEFAULT_CHARSET.name()));
    } catch (UnsupportedEncodingException e) {
      // this should never happen
    }

    StringBuffer tmp = new StringBuffer();
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StringUtilsLabKey.DEFAULT_CHARSET));
      char[] buffer = new char[1024];
      int n = 0;
      while ((n = reader.read(buffer)) != -1) {
        tmp.append(buffer, 0, n);
      }
    } catch (Exception e) {
      System.err.println("File not found: "+e.getMessage());
    }

    String content = tmp.toString();

    System.out.println(content);

    RenderContext context = new BaseRenderContext();
    RenderEngine engine = EngineManager.getInstance();

    System.out.println(engine.render(content, context));
  }
}
