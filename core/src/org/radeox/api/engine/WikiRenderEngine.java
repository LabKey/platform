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

package org.radeox.api.engine;

/**
 * Interface for RenderEngines that know about Wiki pages.
 *
 * @author Stephan J. Schmidt
 * @version $Id: WikiRenderEngine.java,v 1.1 2003/10/07 08:20:24 stephan Exp $
 */

public interface WikiRenderEngine {
  /**
   * Test for the existence of a wiki page
   *
   * @param name Name of an Wiki Page
   * @return result True if wiki page exists
   */

  public boolean exists(String name);

  public boolean showCreate();

  public void appendLink(StringBuffer buffer, String name, String view, String anchor);

  public void appendLink(StringBuffer buffer, String name, String view);

  public void appendCreateLink(StringBuffer buffer, String name, String view);
}
