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
 * Implementation for regex package in JDK 1.4
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: JdkMatcher.java,v 1.5 2004/04/16 09:10:38 stephan Exp $
 */

public class JdkMatcher extends Matcher {
  private JdkPattern pattern;
  private String input;
  private java.util.regex.Matcher internalMatcher;

  public String substitute(Substitution substitution) {
    MatchResult matchResult = new JdkMatchResult(internalMatcher);

    StringBuffer buffer = new StringBuffer();
    while (internalMatcher.find()) {
      internalMatcher.appendReplacement(buffer, "");
      substitution.handleMatch(buffer, matchResult);
    }
    internalMatcher.appendTail(buffer);
    return buffer.toString();
  }

  public String substitute(String substitution) {
    return internalMatcher.replaceAll(substitution);
  }

  protected java.util.regex.Matcher getMatcher() {
    return internalMatcher;
  }

  public JdkMatcher(String input, Pattern pattern) {
    this.input = input;
    this.pattern = (JdkPattern) pattern;
    internalMatcher = this.pattern.getPattern().matcher(this.input);

  }

  public boolean contains() {
    internalMatcher.reset();
    return internalMatcher.find();
  }

  public boolean matches() {
    return internalMatcher.matches();
  }
}