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

package org.radeox.test.filter.mock;

import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.WikiRenderEngine;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.util.Encoder;

import java.io.IOException;
import java.io.Writer;
import java.io.Reader;

public class MockWikiRenderEngine implements RenderEngine, WikiRenderEngine {

  public boolean exists(String name) {
    return name.equals("SnipSnap") || name.equals("stephan");
  }

  public boolean showCreate() {
    return true;
  }

  public void appendLink(StringBuffer buffer, String name, String view, String anchor) {
    buffer.append("link:"+name+"|"+view+"#"+anchor);
  }

  public void appendLink(StringBuffer buffer, String name, String view) {
    buffer.append("link:" + name + "|" +view);
  }

  public void appendCreateLink(StringBuffer buffer, String name, String view) {
    buffer.append("'").append(name).append("' - ");
    buffer.append("'").append(Encoder.escape(name)).append("'");
  }

  public String getName() {
    return "mock-wiki";
  }

  public String render(String content, RenderContext context) {
    return null;
  }

  public void render(Writer out, String content, RenderContext context) throws IOException {
  }


  public String render(Reader in, RenderContext context) throws IOException {
    return null;
  }
}
