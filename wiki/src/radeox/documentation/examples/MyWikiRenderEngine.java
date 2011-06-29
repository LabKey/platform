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

import org.radeox.engine.BaseRenderEngine;
import org.radeox.api.engine.WikiRenderEngine;

// cut:start-1
public class MyWikiRenderEngine
    extends BaseRenderEngine
    implements WikiRenderEngine {

  public boolean exists(String name) {
    // make a lookup in your wiki if the page exists
    return name.equals("SnipSnap") || name.equals("stephan");
  }

  public boolean showCreate() {
    // we always want to show a create link, not only e.g.
    // if a user is registered
    return true;
  }

  public void appendLink(StringBuffer buffer,
                         String name,
                         String view) {
    buffer.append("<a href=\"/show?wiki=");
    buffer.append(name);
    buffer.append("\">");
    buffer.append(view);
    buffer.append("</a>");
  }

  public void appendLink(StringBuffer buffer,
                         String name,
                         String view,
                         String anchor) {
    buffer.append("<a href=\"/show?wiki=");
    buffer.append(name);
    buffer.append("#");
    buffer.append(anchor);
    buffer.append("\">");
    buffer.append(view);
    buffer.append("</a>");
  }

  public void appendCreateLink(StringBuffer buffer,
                               String name,
                               String view) {
    buffer.append(name);
    buffer.append("<a href=\"/create?wiki=");
    buffer.append(name);
    buffer.append("\">");
    buffer.append("?</a>");
  }

  public String getName() {
    return "my-wiki";
  }
}
// cut:end-1