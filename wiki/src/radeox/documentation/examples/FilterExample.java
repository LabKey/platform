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

package examples;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.filter.Filter;
import org.radeox.filter.context.BaseFilterContext;

import java.util.Locale;

/**
 * Example for filters
 *
 * @author Stephan J. Schmidt
 * @version $Id: FilterExample.java,v 1.3 2004/02/04 14:23:37 stephan Exp $
 */

public class FilterExample extends RadeoxTestSupport {
  public FilterExample(String name) {
    super(name);
  }

  public static Test suite() {
    return new TestSuite(MacroExample.class);
  }

  public void testRenderSquare() {
    Filter filter = new SquareFilter();
    String result = filter.filter(" $3 ", new BaseFilterContext());
    assertEquals("Number squared", " 9 ", result);
  }

  public void testRenderSmiley() {
    Filter filter = new SmileyFilter();
    String result = filter.filter(":-(", new BaseFilterContext());
    assertEquals("Smiley  rendered", ":-)", result);
  }

  public void testRenderSmileyFromLocale() {
    Filter filter = new LocaleSmileyFilter();
    InitialRenderContext context = new BaseInitialRenderContext();
    context.set(RenderContext.INPUT_LOCALE, new Locale("mywiki", "mywiki"));
    context.set(RenderContext.OUTPUT_LOCALE, new Locale("mywiki", "mywiki"));
    filter.setInitialContext(context);

    String result = filter.filter(":-(", new BaseFilterContext());
    assertEquals("Smiley  rendered", ":->", result);
  }
}
