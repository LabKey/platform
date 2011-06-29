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
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;

import java.util.Locale;
import java.util.ResourceBundle;

/*
 * Class that extends RegexReplaceFilter but reads patterns from
 * a locale file instead of hardwired regex
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: LocaleRegexReplaceFilter.java,v 1.6 2003/10/07 08:20:24 stephan Exp $
 */

public abstract class LocaleRegexReplaceFilter extends RegexReplaceFilter {
  private static Log log = LogFactory.getLog(LocaleRegexReplaceFilter.class);

  protected abstract String getLocaleKey();

  protected boolean isSingleLine() {
    return false;
  }

  protected ResourceBundle getInputBundle() {
    Locale inputLocale = (Locale) initialContext.get(RenderContext.INPUT_LOCALE);
    String inputName = (String) initialContext.get(RenderContext.INPUT_BUNDLE_NAME);
    return ResourceBundle.getBundle(inputName, inputLocale);
  }

  protected ResourceBundle getOutputBundle() {
    String outputName = (String) initialContext.get(RenderContext.OUTPUT_BUNDLE_NAME);
    Locale outputLocale = (Locale) initialContext.get(RenderContext.OUTPUT_LOCALE);
    return ResourceBundle.getBundle(outputName, outputLocale);
  }

  public void setInitialContext(InitialRenderContext context) {
    super.setInitialContext(context);
    clearRegex();

    ResourceBundle outputMessages =  getOutputBundle();
    ResourceBundle inputMessages = getInputBundle();

    String match = inputMessages.getString(getLocaleKey()+".match");
    String print = outputMessages.getString(getLocaleKey()+".print");
    //System.err.println(getLocaleKey()+": match="+match+" pattern="+print);
    addRegex(match, print, isSingleLine() ? RegexReplaceFilter.SINGLELINE : RegexReplaceFilter.MULTILINE);
  }
}