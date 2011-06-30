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

package org.radeox.api.engine.context;

import org.radeox.api.engine.RenderEngine;

import java.util.Map;


/**
 * RenderContext stores basic data for the context
 * the RenderEngine is called in. RenderContext
 * can be used by the Engine in whatever way it likes to.
 * The Radeox RenderEngine uses RenderContext to
 * construct FilterContext.
 *
 * @author Stephan J. Schmidt
 * @version $Id: RenderContext.java,v 1.2 2004/01/30 08:42:56 stephan Exp $
 */

public interface RenderContext {
  public final static String INPUT_BUNDLE_NAME = "RenderContext.input_bundle_name";
  public final static String OUTPUT_BUNDLE_NAME = "RenderContext.output_bundle_name";
  public final static String LANGUAGE_BUNDLE_NAME = "RenderContext.language_bundle_name";
  public final static String LANGUAGE_LOCALE = "RenderContext.language_locale";
  public final static String INPUT_LOCALE = "RenderContext.input_locale";
  public final static String OUTPUT_LOCALE = "RenderContext.output_locale";
  public final static String DEFAULT_FORMATTER = "RenderContext.default_formatter";

  /**
   * Returns the RenderEngine handling this request.
   *
   * @return engine RenderEngine handling the request within this context
   */
  public RenderEngine getRenderEngine();

  /**
   * Stores the current RenderEngine of the request
   *
   * @param engine Current RenderEnginge
   */
  public void setRenderEngine(RenderEngine engine);

  public Object get(String key);

  public void set(String key, Object value);

  public Map getParameters();

  /**
   * Set the parameters for this execution context. These
   * parameters are read when encountering a variable in
   * macros like {search:$query} or by ParamFilter in {$query}.
   * Query is then read from
   * the parameter map before given to the macro
   *
   * @param parameters Map of parameters with name,value pairs
   */
  public void setParameters(Map parameters);

  public void setCacheable(boolean cacheable);

  public void commitCache();

  public boolean isCacheable();
}
