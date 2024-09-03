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

import org.junit.Before;
import org.junit.Test;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.filter.LinkTestFilter;
import org.radeox.test.filter.mock.MockWikiRenderEngine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LinkTestFilterTest extends FilterTestSupport {
  @Override
  @Before
  public void setUp() throws Exception {
    filter = new LinkTestFilter();
    context.getRenderContext().setRenderEngine(new MockWikiRenderEngine());
    super.setUp();
  }

  @Test
  public void testUrlInLink() {
    assertEquals("Url is reported", "<div class=\"error\">Do not surround URLs with [...].</div>", filter.filter("[http://radeox.org]",context));
  }
  @Test
  public void testCreate() {
    assertEquals("'Roller' - 'Roller'", filter.filter("[Roller]", context));
  }

  @Test
  public void testLink() {
    assertEquals("link:SnipSnap|SnipSnap", filter.filter("[SnipSnap]", context));
  }

  @Test
  public void testLinkLower() {
    assertEquals("link:stephan|stephan", filter.filter("[stephan]", context));
  }

  @Test
  public void testLinkAlias() {
    assertEquals("link:stephan|alias", filter.filter("[alias|stephan]", context));
  }

  @Test
  public void testLinkAliasAnchor() {
    assertEquals("link:stephan|alias#hash", filter.filter("[alias|stephan#hash]", context));
  }

  @Test
  public void testLinkAliasAnchorType() {
    assertEquals("link:stephan|alias#hash", filter.filter("[alias|type:stephan#hash]", context));
  }


  @Test
  public void testLinkCacheable() {
    RenderContext renderContext = context.getRenderContext();
    renderContext.setCacheable(false);
    filter.filter("[SnipSnap]", context);
    renderContext.commitCache();
    assertTrue("Normal link is cacheable", renderContext.isCacheable());
  }

  @Test
  public void testCreateLinkNotCacheable() {
    RenderContext renderContext = context.getRenderContext();
    renderContext.setCacheable(false);
    filter.filter("[Roller]", context);
    renderContext.commitCache();
    assertFalse("Non existing link is not cacheable", renderContext.isCacheable());
  }

  @Test
  public void testLinksWithEscapedChars() {
    assertEquals("'<link>' - '&#60;link&#62;'", filter.filter("[<link>]", context));
  }
}
