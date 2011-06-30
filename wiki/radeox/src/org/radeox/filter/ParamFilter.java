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

import java.util.Map;

/*
 * ParamFilter replaces parametes from from the MacroFilter in the input.
 * These parameters could be read from an HTTP request and put in
 * MacroFilter.
 * A parameter is replaced in {$paramName}
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: ParamFilter.java,v 1.7 2004/04/15 13:56:14 stephan Exp $
 */

public class ParamFilter extends LocaleRegexTokenFilter {
  public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
    Map param = context.getRenderContext().getParameters();

    String name = result.group(1);
    if (param.containsKey(name)) {
      Object value = param.get(name);
      if (value instanceof String[]) {
        buffer.append(((String[]) value)[0]);
      } else {
        buffer.append(value);
      }
    } else {
      buffer.append("<");
      buffer.append(name);
      buffer.append(">");
    }
  }

  protected String getLocaleKey() {
    return "filter.param";
  }

  protected boolean isSingleLine() {
    return true;
  }
}