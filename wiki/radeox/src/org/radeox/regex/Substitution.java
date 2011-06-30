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
 * Called with a MatchResult which is substituted
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: Substitution.java,v 1.2 2004/04/16 07:47:41 stephan Exp $
 */

public interface Substitution {
  /**
   * When substituting matches in a matcher, the handleMatch method
   * of the supplied substitution is called with a MatchResult.
   * This method then does something with the match and replaces
   * the match with some output, like replace all 2*2 with (2*2 =) 4.
   *
   * @param buffer StringBuffer to append the output to
   * @param result MatchResult with the match
   */
    public void handleMatch(StringBuffer buffer, MatchResult result);
}