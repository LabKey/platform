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

package org.radeox.regex;

/*
 * Matcher matches regular expressions (Pattern) to input
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: Matcher.java,v 1.4 2004/04/16 08:52:57 stephan Exp $
 */

public abstract class Matcher {

  /**
   * Create a new matcher object, depending on the implementation
   *
   * @param input Input to match regular expressions agains
   * @param pattern Regular expression pattern
   * @return A Matcher implementation
   */
  public static Matcher create(String input, Pattern pattern) {
    return new JdkMatcher(input, pattern);
  }

  /**
   * Replace all matches in the input with a substitution. For
   * every match substition.handleMatch is called.
   *
   * @param substitution Code which handles every substitution
   * @return String with all matches substituted
   */
  public abstract String substitute(Substitution substitution);

  /**
   * Replace all matches in the input with a string substitution.
   *
   * @param substitution String to replace all matches
   * @return String with all matches substituted
   */
  public abstract String substitute(String substitution);

  /**
   * Test if a regular expression matches the complete input
   *
   * @return True if the regex matches the complete input
   */
  public abstract boolean matches();

  /**
   * Test if a regular expression matches parts of the input
   *
   * @return True if the regex matches a part of the input
   */
  public abstract boolean contains();
}