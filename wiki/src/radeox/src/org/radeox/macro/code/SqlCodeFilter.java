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

package org.radeox.macro.code;

import org.radeox.filter.regex.RegexReplaceFilter;

/*
 * SqlCodeFilter colourizes SQL source code
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: SqlCodeFilter.java,v 1.6 2004/02/19 12:47:56 stephan Exp $
 */

public class SqlCodeFilter extends DefaultRegexCodeFormatter implements SourceCodeFormatter {

  private static final String KEYWORDS =
      "\\b(SELECT|DELETE|UPDATE|WHERE|FROM|GROUP|BY|HAVING)\\b";

  private static final String OBJECTS =
      "\\b(VARCHAR)" +
      "\\b";

  private static final String QUOTES =
      "\"(([^\"\\\\]|\\.)*)\"";


  public SqlCodeFilter() {
    super(QUOTES, "<span class=\"sql-quote\">\"$1\"</span>");
    addRegex(OBJECTS, "<span class=\"sql-object\">$1</span>");
    addRegex(KEYWORDS, "<span class=\"sql-keyword\">$1</span>");
  }


  public String getName() {
    return "sql";
  }

}