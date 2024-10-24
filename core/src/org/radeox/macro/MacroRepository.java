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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.radeox.api.engine.context.InitialRenderContext;

import java.util.*;

/**
 * Repository for plugins
 *
 * @author Stephan J. Schmidt
 * @version $Id: MacroRepository.java,v 1.9 2003/12/17 13:35:36 stephan Exp $
 */

public class MacroRepository extends PluginRepository<Macro> {
  private static Logger log = LogManager.getLogger(MacroRepository.class);

  private InitialRenderContext context;

  protected static MacroRepository instance;
  protected List loaders;

  public synchronized static MacroRepository getInstance() {
    if (null == instance) {
      instance = new MacroRepository();
    }
    return instance;
  }

  private void initialize(InitialRenderContext context) {
    Iterator iterator = list.iterator();
    while (iterator.hasNext()) {
      Macro macro = (Macro) iterator.next();
      macro.setInitialContext(context);
    }
    init();
  }

  public void setInitialContext(InitialRenderContext context) {
    this.context = context;
    initialize(context);
  }

  private void init() {
    Map newPlugins = new HashMap();

    Iterator iterator = list.iterator();
    while (iterator.hasNext()) {
      Macro macro = (Macro) iterator.next();
      newPlugins.put(macro.getName(), macro);
    }
    plugins = newPlugins;
  }

  /**
   * Loads macros from all loaders into plugins.
   */
  private void load() {
    Iterator iterator = loaders.iterator();
    while (iterator.hasNext()) {
      MacroLoader loader = (MacroLoader) iterator.next();
      loader.setRepository(this);
      log.debug("Loading from: " + loader.getClass());
      loader.loadPlugins(this);
    }
  }

  public void addLoader(MacroLoader loader) {
    loader.setRepository(this);
    loaders.add(loader);
    plugins = new HashMap();
    list = new ArrayList();
    load();
  }

  private MacroRepository() {
    loaders = new ArrayList();
    loaders.add(new MacroLoader());
    load();
  }
}
