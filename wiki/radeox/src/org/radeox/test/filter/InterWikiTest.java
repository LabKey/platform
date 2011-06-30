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
package org.radeox.test.filter;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.filter.LinkTestFilter;
import org.radeox.filter.interwiki.InterWiki;
import org.radeox.test.filter.mock.MockInterWikiRenderEngine;

import java.io.StringWriter;
import java.io.IOException;

public class InterWikiTest extends FilterTestSupport {
  public InterWikiTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    filter = new LinkTestFilter();
    context.getRenderContext().setRenderEngine((RenderEngine)
        new MockInterWikiRenderEngine()
    );
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(InterWikiTest.class);
  }

  public void testAnchorInterWiki() {
     assertEquals("<a href=\"http://www.c2.com/cgi/wiki?foo#anchor\">foo@C2</a>", filter.filter("[foo@C2#anchor]", context));
  }

  public void testInterWiki() {
    assertEquals("<a href=\"http://snipsnap.org/space/stephan\">stephan@SnipSnap</a>", filter.filter("[stephan@SnipSnap]", context));
  }

  public void testGoogle() {
    assertEquals("<a href=\"http://www.google.com/search?q=stephan\">stephan@Google</a>", filter.filter("[stephan@Google]", context));
  }

  public void testInterWikiAlias() {
    assertEquals("<a href=\"http://snipsnap.org/space/AliasStephan\">Alias</a>", filter.filter("[Alias|AliasStephan@SnipSnap]", context));
  }

  public void testInterWikiExpander() {
    InterWiki interWiki = InterWiki.getInstance();
    StringWriter writer = new StringWriter();
    try {
      interWiki.expand(writer, "Google", "stephan", "StephanAlias");
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertEquals("<a href=\"http://www.google.com/search?q=stephan\">StephanAlias</a>", writer.toString());
  }

  public void testCacheable() {
    RenderContext renderContext = context.getRenderContext();
    renderContext.setCacheable(false);
    filter.filter("[stephan@SnipSnap]", context);
    assertTrue("InterWiki is cacheable", renderContext.isCacheable());
  }
}
