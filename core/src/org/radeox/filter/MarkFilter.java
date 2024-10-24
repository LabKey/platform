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

import org.radeox.filter.regex.LocaleRegexReplaceFilter;

/*
 * Filter for special words ...
 * Currently if replaces the word neotis with a link to the old
 * neotis homepage. Neotis was the company from funzel and leo.
 * Can be used for other words.
 *
 * @author leo
 * @team other
 * @version $Id: MarkFilter.java,v 1.4 2003/08/13 12:37:05 stephan Exp $
 */

public class MarkFilter extends LocaleRegexReplaceFilter implements CacheFilter {
  @Override
  protected String getLocaleKey() {
    return "filter.mark";
  }
}