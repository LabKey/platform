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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.LocaleRegexTokenFilter;
import org.radeox.regex.MatchResult;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/*
 * Listfilter checks for lists in in its input. These are
 * transformed to output lists, e.g. in HTML. Recognices
 * different lists like numbered lists, unnumbered lists,
 * greek lists, alpha lists etc.
 *
 * @credits nested list support by Davor Cubranic
 * @author stephan
 * @team sonicteam
 * @version $Id: ListFilter.java,v 1.17 2004/04/15 13:56:14 stephan Exp $
 */

public class ListFilter extends LocaleRegexTokenFilter implements CacheFilter {
  private static Logger log = LogManager.getLogger(ListFilter.class);

  private final static Map openList = new HashMap();
  private final static Map closeList = new HashMap();

  private static final String UL_CLOSE = "</ul>";
  private static final String OL_CLOSE = "</ol>";

  @Override
  protected String getLocaleKey() {
    return "filter.list";
  }

  @Override
  protected boolean isSingleLine() {
    return false;
  }

  public ListFilter() {
    super();
    openList.put(Character.valueOf('-'), "<ul class=\"minus\">");
    openList.put(Character.valueOf('*'), "<ul class=\"star\">");
    openList.put(Character.valueOf('#'), "<ol>");
    openList.put(Character.valueOf('i'), "<ol class=\"roman\">");
    openList.put(Character.valueOf('I'), "<ol class=\"ROMAN\">");
    openList.put(Character.valueOf('a'), "<ol class=\"alpha\">");
    openList.put(Character.valueOf('A'), "<ol class=\"ALPHA\">");
    openList.put(Character.valueOf('g'), "<ol class=\"greek\">");
    openList.put(Character.valueOf('h'), "<ol class=\"hiragana\">");
    openList.put(Character.valueOf('H'), "<ol class=\"HIRAGANA\">");
    openList.put(Character.valueOf('k'), "<ol class=\"katakana\">");
    openList.put(Character.valueOf('K'), "<ol class=\"KATAKANA\">");
    openList.put(Character.valueOf('j'), "<ol class=\"HEBREW\">");
    openList.put(Character.valueOf('1'), "<ol>");
    closeList.put(Character.valueOf('-'), UL_CLOSE);
    closeList.put(Character.valueOf('*'), UL_CLOSE);
    closeList.put(Character.valueOf('#'), OL_CLOSE);
    closeList.put(Character.valueOf('i'), OL_CLOSE);
    closeList.put(Character.valueOf('I'), OL_CLOSE);
    closeList.put(Character.valueOf('a'), OL_CLOSE);
    closeList.put(Character.valueOf('A'), OL_CLOSE);
    closeList.put(Character.valueOf('1'), OL_CLOSE);
    closeList.put(Character.valueOf('g'), OL_CLOSE);
    closeList.put(Character.valueOf('G'), OL_CLOSE);
    closeList.put(Character.valueOf('h'), OL_CLOSE);
    closeList.put(Character.valueOf('H'), OL_CLOSE);
    closeList.put(Character.valueOf('k'), OL_CLOSE);
    closeList.put(Character.valueOf('K'), OL_CLOSE);
    closeList.put(Character.valueOf('j'), OL_CLOSE);
  };

  @Override
  public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
    try {
      BufferedReader reader = new BufferedReader(new StringReader(result.group(0)));
      addList(buffer, reader);
    } catch (Exception e) {
      log.warn("ListFilter: unable get list content in " + context.getRenderContext(), e);
    }
  }

  /**
   * Adds a list to a buffer
   *
   * @param buffer The buffer to write to
   * @param reader Input is read from this Reader
   */
  private void addList(StringBuffer buffer, BufferedReader reader) throws IOException {
    char[] lastBullet = new char[0];
    String line = null;
    while ((line = reader.readLine()) != null) {
      // no nested list handling, trim lines:
      line = line.trim();
      if (line.length() == 0) {
        continue;
      }

      int bulletEnd = line.indexOf(' ');
      if (bulletEnd < 1) {
        continue;
      }
      if ( line.charAt(bulletEnd - 1) == '.') {
        bulletEnd--;
      }
      char[] bullet = line.substring(0, bulletEnd).toCharArray();
      // Logger.log("found bullet: ('" + new String(lastBullet) + "') '" + new String(bullet) + "'");
      // check whether we find a new list
      int sharedPrefixEnd;
      for (sharedPrefixEnd = 0; ; sharedPrefixEnd++) {
        if (bullet.length <= sharedPrefixEnd || lastBullet.length <= sharedPrefixEnd ||
          +bullet[sharedPrefixEnd] != lastBullet[sharedPrefixEnd]) {
          break;
        }
      }

      for (int i = sharedPrefixEnd; i < lastBullet.length; i++) {
        //Logger.log("closing " + lastBullet[i]);
        buffer.append(closeList.get(Character.valueOf(lastBullet[i]))).append("\n");
      }

      for (int i = sharedPrefixEnd; i < bullet.length; i++) {
        //Logger.log("opening " + bullet[i]);
        buffer.append(openList.get(Character.valueOf(bullet[i]))).append("\n");
      }
      buffer.append("<li>");
      buffer.append(line.substring(line.indexOf(' ') + 1));
      buffer.append("</li>\n");
      lastBullet = bullet;
    }

    for (int i = lastBullet.length - 1; i >= 0; i--) {
      //Logger.log("closing " + lastBullet[i]);
      buffer.append(closeList.get(Character.valueOf(lastBullet[i])));
    }
  }
}