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

package org.radeox.engine.context;

import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.RenderContext;

import java.util.HashMap;
import java.util.Map;


/**
 * Base implementation for RenderContext
 *
 * @author Stephan J. Schmidt
 * @version $Id: BaseRenderContext.java,v 1.8 2003/10/07 08:20:24 stephan Exp $
 */

public class BaseRenderContext implements RenderContext {
  private boolean cacheable = true;
  private boolean tempCacheable = false;

    private RenderEngine engine;
  private Map<String, String> params;
  private final Map<String, Object> values;
  private final String sourceDescription;

  public BaseRenderContext() {
    this(null);
  }
  public BaseRenderContext(String sourceDescription) {
    values = new HashMap<>();
    this.sourceDescription = sourceDescription;
  }

  @Override
  public Object get(String key) {
    return values.get(key);
  }

  @Override
  public void set(String key, Object value) {
    values.put(key, value);
  }

  @Override
  public Map<String, String> getParameters() {
    return params;
  }

  @Override
  public void setParameters(Map<String, String> parameters) {
    this.params = parameters;
  }

  @Override
  public RenderEngine getRenderEngine() {
    return engine;
  }

  @Override
  public void setRenderEngine(RenderEngine engine) {
    this.engine = engine;
  }

  @Override
  public void setCacheable(boolean cacheable) {
    tempCacheable = cacheable;
  }

  @Override
  public void commitCache() {
    cacheable = cacheable && tempCacheable;
    tempCacheable = false;
  }

  @Override
  public boolean isCacheable() {
    return cacheable;
  }

  public String toString()
  {
    return super.toString() + (sourceDescription == null ? "" : (" " + sourceDescription));
  }
}
