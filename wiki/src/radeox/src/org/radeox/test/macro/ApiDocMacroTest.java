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
import org.radeox.test.macro.MacroTestSupport;
import org.radeox.test.macro.ApiMacroTest;

public class ApiDocMacroTest extends MacroTestSupport {
  public ApiDocMacroTest(String name) {
    super(name);
  }

  public static Test suite() {
    return new TestSuite(ApiDocMacroTest.class);
  }

  public void testApi() {
    String result = EngineManager.getInstance().render("{api-docs}", context);
    // This must be moved to IoC to better test ApiDoc directly.
    assertEquals("ApiDocs are rendered",
        "<table class=\"wiki-table\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th>Binding</th><th>BaseUrl</th><th>Converter" +
        " Name</th></tr><tr class=\"table-odd\"><td>java131</td><td><span class=\"nobr\"><a href=\"http://java.sun.com/j2se/1.3.1/docs/api/\">&#104;ttp://java.sun.com/j2se/1.3.1/docs/api/" +
        "</a></span></td><td>Java</td></tr><tr class=\"table-even\"><td>java</td><td><span class=\"nobr\"><a href=\"http://java.sun.com/j2se/1.4.1/docs/api/\">&#104;ttp://java.sun.com/j2s" +
        "e/1.4.1/docs/api/</a></span></td><td>Java</td></tr><tr class=\"table-odd\"><td>ruby</td><td><span class=\"nobr\"><a href=\"http://www.rubycentral.com/book/ref_c_\">&#104;ttp://ww" +
        "w.rubycentral.com/book/ref_c_</a></span></td><td>Ruby</td></tr><tr class=\"table-even\"><td>radeox</td><td><span class=\"nobr\"><a href=\"http://snipsnap.org/docs/api/\">&#104;tt" +
        "p://snipsnap.org/docs/api/</a></span></td><td>Java</td></tr><tr class=\"table-odd\"><td>nanning</td><td><span class=\"nobr\"><a href=\"http://nanning.sourceforge.net/apidocs/\">&" +
        "#104;ttp://nanning.sourceforge.net/apidocs/</a></span></td><td>Java</td></tr><tr class=\"table-even\"><td>java12</td><td><span class=\"nobr\"><a href=\"http://java.sun.com/j2se/" +
        "1.2/docs/api/\">&#104;ttp://java.sun.com/j2se/1.2/docs/api/</a></span></td><td>Java</td></tr><tr class=\"table-odd\"><td>j2ee</td><td><span class=\"nobr\"><a href=\"http://java.s" +
        "un.com/j2ee/sdk_1.3/techdocs/api/\">&#104;ttp://java.sun.com/j2ee/sdk_1.3/techdocs/api/</a></span></td><td>Java</td></tr></table>",
        result);
  }


}
