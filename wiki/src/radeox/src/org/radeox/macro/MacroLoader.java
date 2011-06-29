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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Plugin loader for macros
 *
 * @author Stephan J. Schmidt
 * @version $Id: MacroLoader.java,v 1.3 2003/06/11 10:04:27 stephan Exp $
 */

public class MacroLoader extends PluginLoader {
  private static Log log = LogFactory.getLog(MacroLoader.class);

  public Class getLoadClass() {
    return Macro.class;
  }

  /**
   * Add a plugin to the known plugin map
   *
   * @param macro Macro to add
   */
  public void add(Repository repository, Object plugin) {
    if (plugin instanceof Macro) {
      repository.put(((Macro) plugin).getName(), plugin);
    } else {
      log.debug("MacroLoader: " + plugin.getClass() + " not of Type " + getLoadClass());
    }
  }

}
