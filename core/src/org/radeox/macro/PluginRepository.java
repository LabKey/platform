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

package org.radeox.macro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for plugins
 *
 * @author Stephan J. Schmidt
 * @version $Id: PluginRepository.java,v 1.5 2003/12/17 13:41:54 stephan Exp $
 */

public class PluginRepository {
  protected Map plugins;
  protected List list;

  protected static PluginRepository instance;

  public PluginRepository() {
    plugins = new HashMap();
    list = new ArrayList();
  }

  public boolean containsKey(String key) {
    return plugins.containsKey(key);
  }

  public Object get(String key) {
    return plugins.get(key);
  }

  public List getPlugins() {
    return new ArrayList(plugins.values());
  }

  public void put(String key, Object value) {
    plugins.put(key, value);
    list.add(value);
  }
}
