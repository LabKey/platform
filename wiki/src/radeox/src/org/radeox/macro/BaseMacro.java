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

import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.macro.parameter.MacroParameter;

import java.io.IOException;
import java.io.Writer;

/*
 * Class that implements base functionality to write macros
 *
 * @author stephan
 * @version $Id: BaseMacro.java,v 1.2 2004/01/30 08:42:57 stephan Exp $
 */

public abstract class BaseMacro implements Macro {
  protected InitialRenderContext initialContext;

  protected String description = " ";
  protected String[] paramDescription = {"unexplained, lazy programmer, probably [funzel]"};

  /**
   * Get the name of the macro. This is used to map a macro
   * in the input to the macro which should be called.
   * The method has to be implemented by subclassing classes.
   *
   * @return name Name of the Macro
   */
  public abstract String getName();

  /**
   * Get a description of the macro. This description explains
   * in a short way what the macro does
   *
   * @return description A string describing the macro
   */
  public String getDescription() {
    return description;
  }

  /**
   * Get a description of the paramters of the macro. The method
   * returns an array with an String entry for every parameter.
   * The format is {"1: description", ...} where 1 is the position
   * of the parameter.
   *
   * @return description Array describing the parameters of the macro
   */
  public String[] getParamDescription() {
    return paramDescription;
  }

  public void setInitialContext(InitialRenderContext context) {
    this.initialContext = context;
  }

  /**
   * Execute the macro. This method is called by MacroFilter to
   * handle macros.
   *
   * @param writer A write where the macro should write its output to
   * @param params Macro parameters with the parameters the macro is called with
   */
  public abstract void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException;

  public String toString() {
    return getName();
  }

  public int compareTo(Object object) {
    Macro macro = (Macro) object;
    return getName().compareTo(macro.getName());
  }
}
