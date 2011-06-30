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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radeox.macro.book.AsinServices;
import org.radeox.macro.parameter.MacroParameter;

import java.io.IOException;
import java.io.Writer;

/*
 * Macro for displaying links to external DVD/CD services or dealers.
 * AsinMacro reads the mapping from names to
 * urls from a configuration file and then maps an ASIN number
 * like {asin:1234} to the DVD/CD e.g. on Amazon.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: AsinMacro.java,v 1.6 2003/08/21 08:50:07 stephan Exp $
 */

public class AsinMacro extends BaseLocaleMacro {
  private static Log log = LogFactory.getLog(AsinMacro.class);

  private String[] paramDescription = {"1: asin number"};

  public String[] getParamDescription() {
    return paramDescription;
  }

  public String getLocaleKey() {
    return "macro.asin";
  }

  public void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException {

    if (params.getLength() == 1) {
      AsinServices.getInstance().appendUrl(writer, params.get("0"));
      return;
    } else {
      log.warn("needs an ASIN number as argument");
      throw new IllegalArgumentException("needs an ASIN number as argument");
    }
  }
}
