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

import org.radeox.macro.PluginRepository;
import org.radeox.macro.Repository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Repository for functions
 *
 * @author Stephan J. Schmidt
 * @version $Id: FunctionRepository.java,v 1.2 2003/05/23 10:47:25 stephan Exp $
 */

public class FunctionRepository extends PluginRepository {
  protected static Repository instance;
  protected List loaders;

  public synchronized  static Repository getInstance() {
    if (null == instance) {
      instance = new FunctionRepository();
    }
    return instance;
  }

 private void load() {
    Iterator iterator = loaders.iterator();
    while (iterator.hasNext()) {
      FunctionLoader loader = (FunctionLoader) iterator.next();
      loader.loadPlugins(this);
    }
  }
  private FunctionRepository() {
    loaders = new ArrayList();
    loaders.add(new FunctionLoader());

    load();
  }
}
