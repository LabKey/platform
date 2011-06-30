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

package org.radeox.api.engine;

import org.radeox.api.engine.context.RenderContext;

import java.io.IOException;
import java.io.Writer;
import java.io.Reader;

/**
 * Interface for RenderEngines. A RenderEngine renders
 * a input string to an output string with the help
 * of filters.
 *
 * @author Stephan J. Schmidt
 * @version $Id: RenderEngine.java,v 1.2 2004/04/14 13:03:25 stephan Exp $
 */

public interface RenderEngine {
  /**
   * Name of the RenderEngine. This is used to get a RenderEngine instance
   * with EngineManager.getInstance(name);
   *
   * @return name Name of the engine
   */
  public String getName();

  /**
   * Render an input with text markup and return a String with
   * e.g. HTML
   *
   * @param content String with the input to render
   * @param context Special context for the render engine, e.g. with
   *                configuration information
   * @return result Output with rendered content
   */
  public String render(String content, RenderContext context);

  /**
   * Render an input with text markup and an write the result
   * e.g. HTML to a writer
   *
   * @param out Writer to write the output to
   * @param content String with the input to render
   * @param context Special context for the render engine, e.g. with
   *                configuration information
   */

  public void render(Writer out, String content, RenderContext context) throws IOException;

  /**
   * Render an input with text markup from a Reader and write the result to a writer
   *
   * @param in Reader to read the input from
   * @param context Special context for the render engine, e.g. with
   *                configuration information
   */
  public String render(Reader in, RenderContext context) throws IOException;

  //public void render(Writer out, Reader in, RenderContext context);
}
