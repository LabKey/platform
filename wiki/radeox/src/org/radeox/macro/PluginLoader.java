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
import org.radeox.util.Service;

import java.util.Iterator;

/**
 * Plugin loader
 *
 * @author Stephan J. Schmidt
 * @version $Id: PluginLoader.java,v 1.6 2004/01/09 12:27:14 stephan Exp $
 */

public abstract class PluginLoader {
  private static Log log = LogFactory.getLog(PluginLoader.class);

  protected Repository repository;

  public Repository loadPlugins(Repository repository) {
    return loadPlugins(repository, getLoadClass());
  }

  public void setRepository(Repository repository) {
    this.repository = repository;
  }

  public Iterator getPlugins(Class klass) {
    return Service.providers(klass);
  }

  public Repository loadPlugins(Repository repository, Class klass) {
    if (null != repository) {
      /* load all macros found in the services plugin control file */
      Iterator iterator = getPlugins(klass);
      while (iterator.hasNext()) {
        try {
          Object plugin = iterator.next();
          add(repository, plugin);
          log.debug("PluginLoader: Loaded plugin: " + plugin.getClass());
        } catch (Exception e) {
          log.warn("PluginLoader: unable to load plugin", e);
        }
      }
    }
    return repository;
  }

  /**
   * Add a plugin to the known plugin map
   *
   * @param plugin Plugin to add
   */
  public abstract void add(Repository repository, Object plugin);

  public abstract Class getLoadClass();
}
