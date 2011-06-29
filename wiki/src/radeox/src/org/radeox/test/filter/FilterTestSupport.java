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

package org.radeox.test.filter;

import junit.framework.TestCase;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.engine.context.BaseRenderContext;
import org.radeox.filter.Filter;
import org.radeox.filter.context.BaseFilterContext;
import org.radeox.filter.context.FilterContext;

/**
 * Support class for defning JUnit FilterTests.
 *
 * @author Stephan J. Schmidt
 * @version $Id: FilterTestSupport.java,v 1.7 2003/08/14 07:46:04 stephan Exp $
 */

public class FilterTestSupport extends TestCase {
  protected Filter filter;
  protected FilterContext context;

  public FilterTestSupport(String s) {
    super(s);
    context = new BaseFilterContext();
    context.setRenderContext(new BaseRenderContext());
  }

  protected void setUp() throws Exception {
    super.setUp();
    if (null != filter) {
      filter.setInitialContext(new BaseInitialRenderContext());
    }
  }
}
