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
import org.radeox.regex.MatchResult;
import org.radeox.regex.Substitution;

/*
 * Filter that calls a special handler method handleMatch() for
 * every occurance of a regular expression.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: RegexTokenFilter.java,v 1.11 2004/04/16 07:47:41 stephan Exp $
 */

public abstract class RegexTokenFilter extends RegexFilter {

  private static Log log = LogFactory.getLog(RegexTokenFilter.class);

  public RegexTokenFilter() {
    super();
  }

  /**
   * create a new regular expression and set
   */
  public RegexTokenFilter(String regex, boolean multiline) {
    super(regex, "", multiline);
  }

  /**
   * create a new regular expression and set
   */
  public RegexTokenFilter(String regex) {
    super(regex, "");
  }

  protected void setUp(FilterContext context) {
  }

  /**
   * Method is called for every occurance of a regular expression.
   * Subclasses have to implement this mehtod.
   *
   * @param buffer Buffer to write replacement string to
   * @param result Hit with the found regualr expression
   * @param context FilterContext for filters
   */
  public abstract void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context);

  public String filter(String input, final FilterContext context) {
    setUp(context);

    String result = null;
    int size = pattern.size();
    for (int i = 0; i < size; i++) {
      Pattern p = (Pattern) pattern.get(i);
      try {
        Matcher m = Matcher.create(input, p);
        result = m.substitute(new Substitution() {
          public void handleMatch(StringBuffer buffer, MatchResult result) {
            RegexTokenFilter.this.handleMatch(buffer, result, context);
          }
        });

        // result = Util.substitute(matcher, p, new ActionSubstitution(s, this, context), result, limit);
      } catch (Exception e) {
        log.warn("<span class=\"error\">Exception</span>: " + this, e);
      } catch (Error err) {
        log.warn("<span class=\"error\">Error</span>: " + this + ": " + err);
        err.printStackTrace();
      }
      input = result;
    }
    return input;
  }
}
