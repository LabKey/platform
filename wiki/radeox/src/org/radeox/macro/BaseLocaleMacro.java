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

package org.radeox.macro;

import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Locale;
import java.util.ResourceBundle;

/*
 * Class that implements base functionality to write macros
 * and reads it's name from a locale file
 *
 * @author stephan
 * @version $Id: BaseLocaleMacro.java,v 1.5 2003/10/07 08:20:24 stephan Exp $
 */

public abstract class BaseLocaleMacro extends BaseMacro implements LocaleMacro {
  private static Log log = LogFactory.getLog(BaseLocaleMacro.class);

  private String name;

  public String getName() {
    return name;
  }

  public void setInitialContext(InitialRenderContext context) {
    super.setInitialContext(context);
    Locale languageLocale = (Locale) context.get(RenderContext.LANGUAGE_LOCALE);
    String languageName = (String) context.get(RenderContext.LANGUAGE_BUNDLE_NAME);
    ResourceBundle messages = ResourceBundle.getBundle(languageName, languageLocale);

    Locale inputLocale = (Locale) context.get(RenderContext.INPUT_LOCALE);
    String inputName = (String) context.get(RenderContext.INPUT_BUNDLE_NAME);
    ResourceBundle inputMessages = ResourceBundle.getBundle(inputName, inputLocale);

    name = inputMessages.getString(getLocaleKey()+".name");

    try {
      description = messages.getString(getLocaleKey()+".description");
    } catch (Exception e) {
      log.warn("Cannot read description from properties " + inputName + " for " + getLocaleKey());
    }
  }
}