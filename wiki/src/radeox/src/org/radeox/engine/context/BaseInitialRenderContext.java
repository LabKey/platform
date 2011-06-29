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

package org.radeox.engine.context;

import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.macro.code.SourceCodeFormatter;

import java.util.Locale;

/**
 * Base impementation for InitialRenderContext
 *
 * @author Stephan J. Schmidt
 * @version $Id: BaseInitialRenderContext.java,v 1.5 2004/01/30 08:42:57 stephan Exp $
 */

public class BaseInitialRenderContext extends BaseRenderContext implements InitialRenderContext {
  public BaseInitialRenderContext() {
    Locale languageLocale = Locale.getDefault();
    Locale locale = new Locale("Basic", "basic");
    set(RenderContext.INPUT_LOCALE, locale);
    set(RenderContext.OUTPUT_LOCALE, locale);
    set(RenderContext.LANGUAGE_LOCALE, languageLocale);
    set(RenderContext.INPUT_BUNDLE_NAME, "radeox_markup");
    set(RenderContext.OUTPUT_BUNDLE_NAME, "radeox_markup");
    set(RenderContext.LANGUAGE_BUNDLE_NAME, "radeox_messages");

    set(RenderContext.DEFAULT_FORMATTER, "java");
  }
}
