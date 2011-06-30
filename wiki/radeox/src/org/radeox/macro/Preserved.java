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
package org.radeox.macro;

import org.radeox.util.Encoder;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * A specialized macro that allows to preserve certain special characters
 * by creating character entities. The subclassing macro may decide whether
 * to call replace() before or after executing the actual macro substitution.
 *
 * @author Matthias L. Jugel
 * @version $Id: Preserved.java,v 1.6 2003/12/17 12:43:08 leo Exp $
 */

public abstract class Preserved extends BaseMacro {
  private Map special = new HashMap();
  private String specialString = "";

  /**
   * Encode special character c by replacing with it's hex character entity code.
   */
  protected void addSpecial(char c) {
    addSpecial("" + c, Encoder.toEntity(c));
  }

  /**
   * Add a replacement for the special character c which may be a string
   *
   * @param c the character to replace
   * @param replacement the new string
   */
  protected void addSpecial(String c, String replacement) {
    specialString += c;
    special.put(c, replacement);
  }

  /**
   * Actually replace specials in source.
   * This method can be used by subclassing macros.
   *
   * @param source String to encode
   *
   * @return encoded Encoded string
   */
  protected String replace(String source) {
    StringBuffer tmp = new StringBuffer();
    StringTokenizer stringTokenizer = new StringTokenizer(source, specialString, true);
    while (stringTokenizer.hasMoreTokens()) {
      String current = stringTokenizer.nextToken();
      if (special.containsKey(current)) {
        current = (String) special.get(current);
      }
      tmp.append(current);
    }
    return tmp.toString();
  }
}
