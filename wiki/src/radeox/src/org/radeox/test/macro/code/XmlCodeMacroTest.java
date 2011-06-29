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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
 * --LICENSE NOTICE--
 */
package org.radeox.test.macro.code;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.EngineManager;
import org.radeox.test.macro.MacroTestSupport;

public class XmlCodeMacroTest extends MacroTestSupport {
  final String S_CODE = "<div class=\"code\"><pre>";
  final String E_CODE = "</pre></div>";
  final String S_XML_TAG = "<span class=\"xml&#45;tag\">&#60;";
  final String E_XML_TAG = "&#62;</span>";
  final String S_XML_KEYWORD = "<span class=\"xml&#45;keyword\">";
  final String E_XML_KEYWORD = "</span>";
  final String S_XML_QUOTE = "<span class=\"xml&#45;quote\">\"";
  final String E_XML_QUOTE = "\"</span>";

  public XmlCodeMacroTest(String name) {
    super(name);
  }

  public static Test suite() {
    return new TestSuite(XmlCodeMacroTest.class);
  }
  public void testXmlCodeXmlElement() {
    String result = EngineManager.getInstance().render("{code:xml}<xml a=\"attr\"><node>text</node></xml>{code}", context);
    assertEquals(
      S_CODE +
      S_XML_TAG + "xml a=" + S_XML_QUOTE + "attr" + E_XML_QUOTE + E_XML_TAG +
      S_XML_TAG + "node" + E_XML_TAG +
      "text" +
      S_XML_TAG + "/node" + E_XML_TAG +
      S_XML_TAG + "/xml" + E_XML_TAG +
      E_CODE,
      result);
  }

  public void testXmlCodeXsl() {
    String sInput = "{code:xml}<xsl:anytag/>{code}";
    String sExpected =
      S_CODE +
      S_XML_TAG +
      S_XML_KEYWORD + "xsl:anytag" + E_XML_KEYWORD + "/" +
      E_XML_TAG +
      E_CODE;
    String sResult = EngineManager.getInstance().render(sInput, context);
    assertEquals(sExpected, sResult);
  }

  public void testXmlCodeXslWithAttr() {
    String sInput = "{code:xml}<xsl:anytag attr=\"1\"/>{code}";
    String sExpected =
      S_CODE +
      S_XML_TAG +
      S_XML_KEYWORD + "xsl:anytag" + E_XML_KEYWORD +
      " attr=" + S_XML_QUOTE + "1" + E_XML_QUOTE + "/" +
      E_XML_TAG +
      E_CODE;
    String sResult = EngineManager.getInstance().render(sInput, context);
    assertEquals(sExpected, sResult);
  }
}

