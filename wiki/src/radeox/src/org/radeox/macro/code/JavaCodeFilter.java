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

package org.radeox.macro.code;

/*
 * JavaCodeFilter colourizes Java source code
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: JavaCodeFilter.java,v 1.5 2003/08/29 12:32:12 stephan Exp $
 */

public class JavaCodeFilter extends DefaultRegexCodeFormatter implements SourceCodeFormatter {

  private static final String KEYWORDS =
      "\\b(abstract|break|byvalue|case|cast|catch|" +
      "const|continue|default|do|else|extends|" +
      "false|final|finally|for|future|generic|goto|if|" +
      "implements|import|inner|instanceof|interface|" +
      "native|new|null|operator|outer|package|private|" +
      "protected|public|rest|return|static|super|switch|" +
      "synchronized|this|throw|throws|transient|true|try|" +
      "var|volatile|while)\\b";

  private static final String OBJECTS =
      "\\b(Boolean|Byte|Character|Class|ClassLoader|Cloneable|Compiler|" +
      "Double|Float|Integer|Long|Math|Number|Object|Process|" +
      "Runnable|Runtime|SecurityManager|Short|String|StringBuffer|" +
      "System|Thread|ThreadGroup|Void|boolean|char|byte|short|int|long|float|double)\\b";

  private static final String QUOTES =
      "\"(([^\"\\\\]|\\.)*)\"";


  public JavaCodeFilter() {
    super(QUOTES, "<span class=\"java-quote\">\"$1\"</span>");
    addRegex(KEYWORDS, "<span class=\"java-keyword\">$1</span>");
    addRegex(OBJECTS, "<span class=\"java-object\">$1</span>");
  }

  public String getName() {
    return "java";
  }
}