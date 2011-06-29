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

package org.radeox.macro.table;

import java.util.StringTokenizer;

/**
 * Built a table from a string
 *
 * @author stephan
 * @version $Id: TableBuilder.java,v 1.3 2003/10/06 08:30:02 stephan Exp $
 */

public class TableBuilder {
  public static Table build(String content) {
    Table table = new Table();
    StringTokenizer tokenizer = new StringTokenizer(content, "|\n", true);
    String lastToken = null;
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if ("\n".equals(token)) {
        // Handles "\n" - "|\n"
        if (null == lastToken || "|".equals(lastToken)) {
          table.addCell(" ");
        }
        table.newRow();
      } else if (!"|".equals(token)) {
        table.addCell(token);
      } else if (null == lastToken || "|".equals(lastToken)) {
        // Handles "|" "||"
        table.addCell(" ");
      }
      lastToken = token;
    }
    return table;
  }
}
