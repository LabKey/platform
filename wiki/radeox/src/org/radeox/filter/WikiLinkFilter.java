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

/*
 * WikiLinkFilter finds WikiLinks in CamelCase in its input and transforms this
 * to <a href="text">...</a> if the wiki page exists. If not
 * it adds a [create text] to the output.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: WikiLinkFilter.java,v 1.3 2003/08/13 12:37:06 stephan Exp $
 */

public class WikiLinkFilter extends LinkTestFilter {
  /**
   * The regular expression for detecting WikiLinks.
   * This is CamelCase or
   * OldAndUglyWikiLinking :-)
   *
   */
  protected String getLocaleKey() {
    return "filter.wikilink";
  }

  protected String getWikiView(String name) {
    return name;
  }

}
