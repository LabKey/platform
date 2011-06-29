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
import org.radeox.api.engine.context.RenderContext;
import org.radeox.filter.LinkTestFilter;
import org.radeox.test.filter.mock.MockWikiRenderEngine;

public class LinkTestFilterTest extends FilterTestSupport {
  public LinkTestFilterTest(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    filter = new LinkTestFilter();
    context.getRenderContext().setRenderEngine(new MockWikiRenderEngine());
    super.setUp();
  }

  public static Test suite() {
    return new TestSuite(LinkTestFilterTest.class);
  }

  public void testUrlInLink() {
    assertEquals("Url is reported", "<div class=\"error\">Do not surround URLs with [...].</div>", filter.filter("[http://radeox.org]",context));
  }
  public void testCreate() {
    assertEquals("'Roller' - 'Roller'", filter.filter("[Roller]", context));
  }

  public void testLink() {
    assertEquals("link:SnipSnap|SnipSnap", filter.filter("[SnipSnap]", context));
  }

  public void testLinkLower() {
    assertEquals("link:stephan|stephan", filter.filter("[stephan]", context));
  }

  public void testLinkAlias() {
    assertEquals("link:stephan|alias", filter.filter("[alias|stephan]", context));
  }

  public void testLinkAliasAnchor() {
    assertEquals("link:stephan|alias#hash", filter.filter("[alias|stephan#hash]", context));
  }

  public void testLinkAliasAnchorType() {
    assertEquals("link:stephan|alias#hash", filter.filter("[alias|type:stephan#hash]", context));
  }


  public void testLinkCacheable() {
    RenderContext renderContext = context.getRenderContext();
    renderContext.setCacheable(false);
    filter.filter("[SnipSnap]", context);
    renderContext.commitCache();
    assertTrue("Normal link is cacheable", renderContext.isCacheable());
  }

  public void testCreateLinkNotCacheable() {
    RenderContext renderContext = context.getRenderContext();
    renderContext.setCacheable(false);
    filter.filter("[Roller]", context);
    renderContext.commitCache();
    assertTrue("Non existing link is not cacheable", ! renderContext.isCacheable());
  }

  public void testLinksWithEscapedChars() {
    assertEquals("'<link>' - '&#60;link&#62;'", filter.filter("[<link>]", context));
  }
}
