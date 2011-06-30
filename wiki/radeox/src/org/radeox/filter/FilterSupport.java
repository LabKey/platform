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

package org.radeox.filter;

import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.context.FilterContext;

/*
 * Abstract Filter Class that supplies the
 * Filter interface. Concrete Filters should
 * inherit from Filter. Filters transform a
 * String (usually snip content) to another String
 * (usually HTML).
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: FilterSupport.java,v 1.9 2003/10/07 08:20:24 stephan Exp $
 */

public abstract class FilterSupport implements Filter {
  protected InitialRenderContext initialContext;

  public FilterSupport() {
  }

  public String[] replaces() {
    return FilterPipe.NO_REPLACES;
  }

  public String[] before() {
    return FilterPipe.EMPTY_BEFORE;
  }

  public void setInitialContext(InitialRenderContext context) {
     this.initialContext = context;
  }

  public String getDescription() {
    return "";
  }
}