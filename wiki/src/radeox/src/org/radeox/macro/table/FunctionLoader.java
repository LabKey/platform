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

package org.radeox.macro.table;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radeox.macro.PluginLoader;
import org.radeox.macro.Repository;

/**
 * Plugin loader for table functions
 *
 * @author Stephan J. Schmidt
 * @version $Id: FunctionLoader.java,v 1.4 2003/08/14 07:46:04 stephan Exp $
 */

public class FunctionLoader extends PluginLoader {
  private static Log log = LogFactory.getLog(FunctionLoader.class);

  protected static FunctionLoader instance;

  public static synchronized PluginLoader getInstance() {
    if (null == instance) {
      instance = new FunctionLoader();
    }
    return instance;
  }

  public Class getLoadClass() {
    return Function.class;
  }

  /**
   * Add a plugin to the known plugin map
   *
   * @param plugin Function to add
   */
  public void add(Repository repository, Object plugin) {
    if (plugin instanceof Function) {
      repository.put(((Function) plugin).getName().toLowerCase(), plugin);
    } else {
      log.debug("FunctionLoader: " + plugin.getClass() + " not of Type " + getLoadClass());
    }
  }

}
