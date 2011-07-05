/*
 * This file is part of "SnipSnap Radeox Rendering Engine".
 *
 * Copyright (c) 2002 Stephan J. Schmidt, Matthias L. Jugel
 * All Rights Reserved.
 *
 * Please visit http://radeox.org/ for updates and contact.
 *
 * Modified 6/30/2011 by Isaac Hodes
 * commented out encoding of & in order to correctly display URLs
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

package org.radeox.util;

import org.radeox.regex.Pattern;
import org.radeox.regex.Matcher;
import org.radeox.regex.Substitution;
import org.radeox.regex.MatchResult;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/*
 * Escapes and encodes Strings for web usage
 *
 * @author stephan
 * @version $Id: Encoder.java,v 1.7 2004/04/16 07:47:41 stephan Exp $
 */

public class Encoder {
  private final static String DELIMITER = "&\"'<>";
  private final static Map ESCAPED_CHARS = new HashMap();
  // private final static Pattern entityPattern = Pattern.compile("&(#?[0-9a-fA-F]+);");

  static {
    //ESCAPED_CHARS.put("&", toEntity('&'));
    ESCAPED_CHARS.put("\"", toEntity('"'));
    ESCAPED_CHARS.put("'", toEntity('\''));
    ESCAPED_CHARS.put(">", toEntity('>'));
    ESCAPED_CHARS.put("<", toEntity('<'));
  }

  /**
   * Encoder special characters that may occur in a HTML so it can be displayed
   * safely.
   * @param str the original string
   * @return the escaped string
   */
  public static String escape(String str) {
    StringBuffer result = new StringBuffer();
    StringTokenizer tokenizer = new StringTokenizer(str, DELIMITER, true);
    while(tokenizer.hasMoreTokens()) {
      String currentToken = tokenizer.nextToken();
      if(ESCAPED_CHARS.containsKey(currentToken)) {
        result.append(ESCAPED_CHARS.get(currentToken));
      } else {
        result.append(currentToken);
      }
    }
    return result.toString();
  }

  public static String unescape(String str) {
    StringBuffer result = new StringBuffer();

    org.radeox.regex.Compiler compiler = org.radeox.regex.Compiler.create();
    Pattern entityPattern = compiler.compile("&(#?[0-9a-fA-F]+);");

    Matcher matcher = Matcher.create(str, entityPattern);
    result.append(matcher.substitute(new Substitution() {
      public void handleMatch(StringBuffer buffer, MatchResult result) {
        buffer.append(toChar(result.group(1)));
      }
    }));
    return result.toString();
  }

  public static String toEntity(int c) {
    return "&#" + c + ";";
  }

  public static char toChar(String number) {
    return (char) Integer.decode(number.substring(1)).intValue();
  }
}
