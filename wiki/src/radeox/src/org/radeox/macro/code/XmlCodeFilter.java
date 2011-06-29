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

/*
 * XmlCodeFilter colourizes Xml Code
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: XmlCodeFilter.java,v 1.6 2003/12/11 13:24:56 leo Exp $
 */

public class XmlCodeFilter extends DefaultRegexCodeFormatter implements SourceCodeFormatter {
  private static final String KEYWORDS = "\\b(xsl:[^&\\s]*)\\b";
  private static final String TAGS = "(&#60;/?.*?&#62;)";
  private static final String QUOTE = "\"(([^\"\\\\]|\\.)*)\"";

  public XmlCodeFilter() {
    super(QUOTE, "<span class=\"xml-quote\">\"$1\"</span>");
    addRegex(TAGS, "<span class=\"xml-tag\">$1</span>");
    addRegex(KEYWORDS, "<span class=\"xml-keyword\">$1</span>");
  }

  public String getName() {
    return "xml";
  }
}
