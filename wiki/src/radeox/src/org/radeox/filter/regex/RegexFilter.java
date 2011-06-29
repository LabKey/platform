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
import org.radeox.filter.FilterSupport;
import org.radeox.filter.context.FilterContext;

import java.util.ArrayList;
import java.util.List;

/*
 * Class that stores regular expressions, can be subclassed
 * for special Filters
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: RegexFilter.java,v 1.11 2004/04/15 13:56:14 stephan Exp $
 */

public abstract class RegexFilter extends FilterSupport {
  private static Log log = LogFactory.getLog(RegexFilter.class);

  protected List pattern = new ArrayList();
  protected List substitute = new ArrayList();

  public final static boolean SINGLELINE = false;
  public final static boolean MULTILINE = true;

  // TODO future use
  //private RegexService regexService;

  public RegexFilter() {
    super();
  }

  /**
   * create a new regular expression that takes input as multiple lines
   */
  public RegexFilter(String regex, String substitute) {
    this();
    addRegex(regex, substitute);
  }

  /**
   * create a new regular expression and set
   */
  public RegexFilter(String regex, String substitute, boolean multiline) {
    addRegex(regex, substitute, multiline);
  }

  public void clearRegex() {
    pattern.clear();
    substitute.clear();
  }
  public void addRegex(String regex, String substitute) {
    addRegex(regex, substitute, MULTILINE);
  }

  public void addRegex(String regex, String substitute, boolean multiline) {
    // compiler.compile(regex, (multiline ? Perl5Compiler.MULTILINE_MASK : Perl5Compiler.SINGLELINE_MASK) | Perl5Compiler.READ_ONLY_MASK));
    try {
      org.radeox.regex.Compiler compiler = org.radeox.regex.Compiler.create();
      compiler.setMultiline(multiline);
      this.pattern.add(compiler.compile(regex));
      // Pattern.DOTALL
      this.substitute.add(substitute);
    } catch (Exception e) {
      log.warn("bad pattern: " + regex + " -> " + substitute+" "+e);
    }
  }

  public abstract String filter(String input, FilterContext context);
}
