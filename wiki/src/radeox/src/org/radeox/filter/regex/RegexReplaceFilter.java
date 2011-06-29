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

package org.radeox.filter.regex;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radeox.filter.context.FilterContext;
import org.radeox.regex.Pattern;
import org.radeox.regex.Matcher;


/*
 * Class that applies a RegexFilter, can be subclassed
 * for special Filters. Regular expressions in the input
 * are replaced with strings.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: RegexReplaceFilter.java,v 1.8 2004/04/15 13:56:14 stephan Exp $
 */

public class RegexReplaceFilter extends RegexFilter {
  private static Log log = LogFactory.getLog(RegexReplaceFilter.class);

  public RegexReplaceFilter() {
    super();
  }

  public RegexReplaceFilter(String regex, String substitute) {
    super(regex, substitute);
  }

  public RegexReplaceFilter(String regex, String substitute, boolean multiline) {
    super(regex, substitute, multiline);
  }

  public String filter(String input, FilterContext context) {
    String result = input;
    int size = pattern.size();
    Pattern p;
    String s;
    for (int i = 0; i < size; i++) {
      p = (Pattern) pattern.get(i);
      s = (String) substitute.get(i);
      try {
        Matcher matcher = Matcher.create(result, p);
        result = matcher.substitute(s);

        // Util.substitute(matcher, p, new Perl5Substitution(s, interps), result, limit);
      } catch (Exception e) {
        //log.warn("<span class=\"error\">Exception</span>: " + this + ": " + e);
        log.warn("Exception for: " + this+" "+e);
     } catch (Error err) {
        //log.warn("<span class=\"error\">Error</span>: " + this + ": " + err);
        log.warn("Error for: " + this);
        err.printStackTrace();
      }
    }
    return result;
  }
}