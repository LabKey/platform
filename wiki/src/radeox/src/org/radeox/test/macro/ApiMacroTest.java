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
package org.radeox.test.macro;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.EngineManager;

public class ApiMacroTest extends MacroTestSupport {
  public ApiMacroTest(String name) {
    super(name);
  }

  public static Test suite() {
    return new TestSuite(ApiMacroTest.class);
  }

  public void testApi() {
    String result = EngineManager.getInstance().render("{api:java.lang.object}", context);
    assertEquals("<a href=\"http://java.sun.com/j2se/1.4.1/docs/api/java/lang/object.html\">java.lang.object</a>", result);
  }

  public void testRuby() {
    String result = EngineManager.getInstance().render("{api:String@Ruby}", context);
    assertEquals("Ruby namespace is used","<a href=\"http://www.rubycentral.com/book/ref_c_string.html\">String</a>", result);
  }

}
