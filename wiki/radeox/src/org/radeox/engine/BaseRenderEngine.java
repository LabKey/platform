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

package org.radeox.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radeox.EngineManager;
import org.radeox.api.engine.RenderEngine;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.filter.Filter;
import org.radeox.filter.FilterPipe;
import org.radeox.filter.context.BaseFilterContext;
import org.radeox.filter.context.FilterContext;
import org.radeox.util.Service;

import java.io.*;
import java.util.Iterator;

/**
 * Base implementation of RenderEngine
 *
 * @author Stephan J. Schmidt
 * @version $Id: BaseRenderEngine.java,v 1.17 2004/04/14 13:03:25 stephan Exp $
 */

public class BaseRenderEngine implements RenderEngine {
  private static Log log = LogFactory.getLog(BaseRenderEngine.class);

  protected InitialRenderContext initialContext;
  protected FilterPipe fp;

  public BaseRenderEngine(InitialRenderContext context) {
     this.initialContext = context;
  }

  public BaseRenderEngine() {
    this(new BaseInitialRenderContext());
  }

  protected void init() {
    if (null == fp) {
      fp = new FilterPipe(initialContext);

      Iterator iterator = Service.providers(Filter.class);
      while (iterator.hasNext()) {
        try {
          Filter filter = (Filter) iterator.next();
          fp.addFilter(filter);
          log.debug("Loaded filter: " + filter.getClass().getName());
        } catch (Exception e) {
          log.warn("BaseRenderEngine: unable to load filter", e);
        }
      }

      fp.init();
      //Logger.debug("FilterPipe = "+fp.toString());
    }
  }

  /**
   * Name of the RenderEngine. This is used to get a RenderEngine instance
   * with EngineManager.getInstance(name);
   *
   * @return name Name of the engine
   */
  public String getName() {
    return EngineManager.DEFAULT;
  }

  /**
   * Render an input with text markup and return a String with
   * e.g. HTML
   *
   * @param content String with the input to render
   * @param context Special context for the filter engine, e.g. with
   *                configuration information
   * @return result Output with rendered content
   */
  public String render(String content, RenderContext context) {
    init();
    FilterContext filterContext = new BaseFilterContext();
    filterContext.setRenderContext(context);
    return fp.filter(content, filterContext);
  }

  /**
   * Render an input with text markup from a Reader and write the result to a writer
   *
   * @param in Reader to read the input from
   * @param context Special context for the render engine, e.g. with
   *                configuration information
   */
  public String render(Reader in, RenderContext context) throws IOException {
    StringBuffer buffer = new StringBuffer();
    BufferedReader inputReader = new BufferedReader(in);
    String line;
    while ((line = inputReader.readLine()) != null) {
        buffer.append(line);
    }
    return render(buffer.toString(), context);
 }

  public void render(Writer out, String content, RenderContext context) throws IOException {
    out.write(render(content, context));
  }
}
