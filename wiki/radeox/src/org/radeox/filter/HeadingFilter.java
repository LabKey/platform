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

import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.LocaleRegexTokenFilter;
import org.radeox.regex.MatchResult;
import org.radeox.api.engine.context.InitialRenderContext;

import java.text.MessageFormat;

/*
 * Transforms header style lines into subsections. A header starts with a 1 for
 * first level headers and 1.1 for secend level headers. Headers are
 * numbered automatically
 *
 * @author leo
 * @team other
 * @version $Id: HeadingFilter.java,v 1.8 2004/04/15 13:56:14 stephan Exp $
 */

public class HeadingFilter extends LocaleRegexTokenFilter implements CacheFilter {
  private MessageFormat formatter;


  protected String getLocaleKey() {
    return "filter.heading";
  }

  public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
    buffer.append(handleMatch(result, context));
  }

  public void setInitialContext(InitialRenderContext context) {
    super.setInitialContext(context);
    String outputTemplate = outputMessages.getString(getLocaleKey()+".print");
    formatter = new MessageFormat("");
    formatter.applyPattern(outputTemplate);
 }

  public String handleMatch(MatchResult result, FilterContext context) {
    return formatter.format(new Object[]{result.group(1).replace('.', '-'), result.group(3)});
  }
}
