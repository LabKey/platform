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

package org.radeox.filter.context;

import org.radeox.api.engine.context.RenderContext;
import org.radeox.macro.parameter.BaseMacroParameter;
import org.radeox.macro.parameter.MacroParameter;


/**
 * Base impementation for FilterContext
 *
 * @author Stephan J. Schmidt
 * @version $Id: BaseFilterContext.java,v 1.6 2003/10/07 08:20:24 stephan Exp $
 */

public class BaseFilterContext implements FilterContext {
  protected RenderContext context;

  public MacroParameter getMacroParameter() {
    return new BaseMacroParameter(context);
  }

  public void setRenderContext(RenderContext context) {
    this.context = context;
  }

  public RenderContext getRenderContext() {
    return context;
  }
}
