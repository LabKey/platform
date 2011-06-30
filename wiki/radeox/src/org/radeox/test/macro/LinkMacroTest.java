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
import org.radeox.api.engine.RenderEngine;

public class LinkMacroTest extends MacroTestSupport {
  public RenderEngine engine;

  public LinkMacroTest(String name) {
    super(name);
  engine= EngineManager.getInstance();

  }

  public static Test suite() {
    return new TestSuite(LinkMacroTest.class);
  }

//  public void testFile() {
//    String result = EngineManager.getInstance().render("{link:Test|c:\\some\\file}", context);
//    assertEquals("<a href=\"\"></a>", result);
//  }

  public void testSimpleLink() {
    String result = engine.render("{link:TEST|http://foo.com/}", context);
    assertEquals("<span class=\"nobr\"><a href=\"http://foo.com/\">TEST</a></span>", result);
  }

  public void testSimpleLinkWithoutName() {
    String result = engine.render("{link:http://foo.com/}", context);
    assertEquals("<span class=\"nobr\"><a href=\"http://foo.com/\">&#104;ttp://foo.com/</a></span>", result);
  }

  public void testCorrectEndWithSpace() {
    String result = engine.render("{link:TEST|http://foo.com/} ", context);
    assertEquals("<span class=\"nobr\"><a href=\"http://foo.com/\">TEST</a></span> ", result);
  }

  public void testCorrectEndWithComma() {
    String result = engine.render("{link:TEST|http://foo.com/},", context);
    assertEquals("<span class=\"nobr\"><a href=\"http://foo.com/\">TEST</a></span>,", result);
  }

  public void testCorrectEndWithSpaceAndComma() {
    String result = engine.render("{link:TEST|http://foo.com/} ,", context);
    assertEquals("<span class=\"nobr\"><a href=\"http://foo.com/\">TEST</a></span> ,", result);
  }

  public void testSimpleLinkWithoutNameAndComma() {
    String result = engine.render("{link:http://foo.com/},", context);
    assertEquals("<span class=\"nobr\"><a href=\"http://foo.com/\">&#104;ttp://foo.com/</a></span>,", result);
  }

  public void testLinkWithAmpersand() {
    String result = engine.render("{link:test|http://foo.com/foo.cgi?test=aaa&test1=bbb},", context);
    assertEquals("<span class=\"nobr\"><a href=\"http://foo.com/foo.cgi?test=aaa&#38;test1=bbb\">test</a></span>,", result);
  }

}
