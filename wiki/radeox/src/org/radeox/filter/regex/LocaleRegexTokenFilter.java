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
 * Filter that extends RegexTokenFilter but reads regular expressions from
 * a locale
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: LocaleRegexTokenFilter.java,v 1.5 2003/10/07 08:20:24 stephan Exp $
 */

public abstract class LocaleRegexTokenFilter extends RegexTokenFilter  {
  private static Log log = LogFactory.getLog(LocaleRegexTokenFilter.class);
  protected ResourceBundle inputMessages;
  protected ResourceBundle outputMessages;

  protected boolean isSingleLine() {
    return false;
  }

  protected ResourceBundle getInputBundle() {
    Locale inputLocale = (Locale) initialContext.get(RenderContext.INPUT_LOCALE);
    String inputName = (String) initialContext.get(RenderContext.INPUT_BUNDLE_NAME);
    return ResourceBundle.getBundle(inputName, inputLocale);
  }

  protected ResourceBundle getOutputBundle() {
    Locale outputLocale = (Locale) initialContext.get(RenderContext.OUTPUT_LOCALE);
    String outputName = (String) initialContext.get(RenderContext.OUTPUT_BUNDLE_NAME);
    return ResourceBundle.getBundle(outputName, outputLocale);
  }

  public void setInitialContext(InitialRenderContext context) {
    super.setInitialContext(context);
    clearRegex();

    outputMessages = getOutputBundle();
    inputMessages = getInputBundle();
    String match = inputMessages.getString(getLocaleKey()+".match");
    addRegex(match, "", isSingleLine() ? RegexReplaceFilter.SINGLELINE : RegexReplaceFilter.MULTILINE);
  }

  protected abstract String getLocaleKey();
}
