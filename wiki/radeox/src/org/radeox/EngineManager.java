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

package org.radeox;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.api.engine.RenderEngine;
import org.radeox.util.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Acess point to dock several different rendering engines into
 * e.g. SnipSnap.
 *
 * Will be replaced by PicoContainer (but kept for compatibility)
 *
 *
 * @author Stephan J. Schmidt
 * @version $Id: EngineManager.java,v 1.14 2003/10/07 08:20:24 stephan Exp $
 */

public class EngineManager  {
  private static Log log = LogFactory.getLog(EngineManager.class);

  public static final String DEFAULT = "radeox";
  private static Map availableEngines = new HashMap();


  static {
    Iterator iterator = Service.providers(RenderEngine.class);
    while (iterator.hasNext()) {
      try {
        RenderEngine engine = (RenderEngine) iterator.next();
        registerEngine(engine);
        log.debug("Loaded RenderEngine: " + engine.getClass().getName());
      } catch (Exception e) {
        log.warn("EngineManager: unable to load RenderEngine", e);
      }
    }
  }

  /**
   * Different RenderEngines can register themselves with the
   * EngineManager factory to be available with EngineManager.getInstance();
   *
   * @param engine RenderEngine instance, e.g. SnipRenderEngine
   */
  public static synchronized void registerEngine(RenderEngine engine) {
    if (null == availableEngines) {
      availableEngines = new HashMap();
    }
    availableEngines.put(engine.getName(), engine);
  }

  /**
   * Get an instance of a RenderEngine. This is a factory method.
   *
   * @param name Name of the RenderEngine to get
   * @return engine RenderEngine for the requested name
   */
  public static synchronized RenderEngine getInstance(String name) {
    if (null == availableEngines) {
      availableEngines = new HashMap();
    }

    //Logger.debug("Engines: " + availableEngines);
    return (RenderEngine) availableEngines.get(name);
  }

  /**
   * Get an instance of a RenderEngine. This is a factory method.
   * Defaults to a default RenderEngine. Currently this is a
   * basic EngineManager with no additional features that is
   * distributed with Radeox.
   *
   * @return engine default RenderEngine
   */
  public static synchronized RenderEngine getInstance() {
    //availableEngines = null;
    if (null == availableEngines) {
      availableEngines = new HashMap();
    }

    if (!availableEngines.containsKey(DEFAULT)) {
      RenderEngine engine = new BaseRenderEngine();
      availableEngines.put(engine.getName(), engine);
    }

    return (RenderEngine) availableEngines.get(DEFAULT);
  }

  public static String getVersion() {
    return "0.5.1";
  }
}
