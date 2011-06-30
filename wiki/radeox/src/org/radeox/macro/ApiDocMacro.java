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
import org.radeox.macro.api.ApiDoc;
import org.radeox.macro.parameter.MacroParameter;

import java.io.IOException;
import java.io.Writer;

/*
 * Lists all known API documentation repositories and
 * mappings
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: ApiDocMacro.java,v 1.6 2003/08/21 08:50:07 stephan Exp $
 */

public class ApiDocMacro extends BaseLocaleMacro {
  private static Log log = LogFactory.getLog(ApiDocMacro.class);

  private String[] paramDescription = {};

  public String[] getParamDescription() {
    return paramDescription;
  }

  public String getLocaleKey() {
    return "macro.apidocs";
  }

  public void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException {
    ApiDoc apiDoc = ApiDoc.getInstance();
    apiDoc.appendTo(writer);
    return;
  }
}
