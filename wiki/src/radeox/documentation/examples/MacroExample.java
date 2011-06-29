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
import org.radeox.macro.Macro;
import org.radeox.macro.parameter.BaseMacroParameter;
import org.radeox.macro.parameter.MacroParameter;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.engine.context.BaseInitialRenderContext;

import java.io.*;

/**
 * Example for a HelloWorldMacro
 *
 * @author Stephan J. Schmidt
 * @version $Id: MacroExample.java,v 1.4 2004/02/06 07:46:17 stephan Exp $
 */

public class MacroExample extends RadeoxTestSupport {
  public MacroExample(String name) {
    super(name);
  }

  public static Test suite() {
    return new TestSuite(MacroExample.class);
  }

  public void testRenderHelloWorld() {
    Macro macro = new HelloWorldMacro();
    StringWriter writer = new StringWriter();
    try {
      macro.execute(writer, new BaseMacroParameter());
    } catch (IllegalArgumentException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (IOException e) {
      //
    }
    assertEquals("Hello world rendered", "hello world", writer.toString());

  }

//  public void testGroovyMacro() {
//    GroovyMacroCompiler compiler = new GroovyMacroCompiler();
//    StringBuffer contentOfFile = new StringBuffer();
//    try {
//      BufferedReader br = new BufferedReader(
//          new InputStreamReader(
//              new FileInputStream("GroovyMacro.groovy")));
//
//      String line;
//      while ((line = br.readLine()) != null) {
//        contentOfFile.append(line);
//      }
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//    String content = contentOfFile.toString();
//    Macro macro = compiler.compileMacro(content);
//    assertNotNull("Groovy Macro did compile.", macro);
//  }

  public void testCompiledGroovyMacro() {
    Macro macro = new GroovyMacro();

    StringWriter writer = new StringWriter();
    try {
      MacroParameter params = new BaseMacroParameter();
      macro.execute(writer, params);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (IOException e) {
      //
    }
    assertEquals("Hello world from Groovy is rendered", "Yipee ay ey, schweinebacke", writer.toString());
  }

  public void testRenderHelloWorldWithIntialRenderContext() {
    Macro macro = new InitialRenderContextHelloWorldMacro();
    StringWriter writer = new StringWriter();
    try {
      MacroParameter params = new BaseMacroParameter();
      InitialRenderContext context = new BaseInitialRenderContext();
      context.set("hello.name", "stephan");
      macro.setInitialContext(context);
      macro.execute(writer, params);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (IOException e) {
      //
    }
    assertEquals("Hello world with InitialRenderContext rendered", "hello stephan", writer.toString());

  }

  public void testRenderHelloWorldWithParameter() {
    Macro macro = new ParameterHelloWorldMacro();
    StringWriter writer = new StringWriter();
    try {
      MacroParameter params = new BaseMacroParameter();
      params.setParams("stephan");
      macro.execute(writer, params);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (IOException e) {
      //
    }
    assertEquals("Hello world with parameter rendered", "Hello <b>stephan</b>", writer.toString());

  }

}
