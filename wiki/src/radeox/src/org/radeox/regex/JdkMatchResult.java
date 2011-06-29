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
 * The result when a Matcher object finds matches in some input
 * Implementation for regex package in JDK 1.4
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: JdkMatchResult.java,v 1.1 2004/04/16 07:37:52 stephan Exp $
 */

public class JdkMatchResult extends MatchResult {
  private java.util.regex.Matcher matcher;

  public JdkMatchResult(java.util.regex.Matcher matcher) {
    this.matcher = matcher;
  }

  public JdkMatchResult(Matcher matcher) {
    this.matcher = ((JdkMatcher) matcher).getMatcher();
  }

  public int groups() {
    return matcher.groupCount();
  }

  public String group(int i) {
    return matcher.group(i);
  }

  public int beginOffset(int i) {
    return matcher.start(i);
  }

  public int endOffset(int i) {
    return matcher.end(i);
  }
}