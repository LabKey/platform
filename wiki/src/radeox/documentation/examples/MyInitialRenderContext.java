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

package examples;

import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.context.BaseRenderContext;

import java.util.Locale;

/**
 * Example impementation for InitialRenderContext
 *
 * @author Stephan J. Schmidt
 * @version $Id: MyInitialRenderContext.java,v 1.2 2004/02/04 08:39:40 stephan Exp $
 */

// cut:start-1
public class MyInitialRenderContext
    extends BaseRenderContext
    implements InitialRenderContext {

  public MyInitialRenderContext() {
    Locale languageLocale = Locale.getDefault();
    Locale locale = new Locale("mywiki", "mywiki");
    set(RenderContext.INPUT_LOCALE, locale);
    set(RenderContext.OUTPUT_LOCALE, locale);
    set(RenderContext.LANGUAGE_LOCALE, languageLocale);
    set(RenderContext.INPUT_BUNDLE_NAME, "my_markup");
    set(RenderContext.OUTPUT_BUNDLE_NAME, "my_markup");
    set(RenderContext.LANGUAGE_BUNDLE_NAME, "my_messages");
  }
}
// cut:end-1
