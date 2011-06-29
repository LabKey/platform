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
 * Class that compiles regular expressions to patterns
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: Compiler.java,v 1.2 2004/04/16 08:52:57 stephan Exp $
 */

public abstract class Compiler {
  /**
   * Create a new Compiler object depending on the used implementation
   *
   * @return Compiler object with the used implementation
   */
  public static Compiler create() {
    return new JdkCompiler();
  }

  /**
   * Whether the compiler should create multiline patterns
   * or single line patterns.
   *
   * @param multiline True if the pattern is multiline, otherwise false
   */
  public abstract void setMultiline(boolean multiline);

  /**
   * Compile a String regular expression to a regex pattern
   *
   * @param regex String representation of a regular expression
   * @return Compiled regular expression
   */
  public abstract Pattern compile(String regex);
}