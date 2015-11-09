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

import org.radeox.macro.parameter.MacroParameter;
import org.radeox.macro.xref.XrefMapper;

import java.io.IOException;
import java.io.Writer;

/*
 * Macro that replaces {xref} with external URLS to xref
 * source code
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: XrefMacro.java,v 1.5 2003/08/21 08:50:07 stephan Exp $
 */

public class XrefMacro extends BaseLocaleMacro {
  private String[] paramDescription =
      {"1: class name, e.g. java.lang.Object or java.lang.Object@Nanning",
       "?2: line number"};

  public String[] getParamDescription() {
    return paramDescription;
  }

  public String getLocaleKey() {
    return "macro.xref";
  }

  public void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException {
    String project;
    String klass;
    int lineNumber = 0;

    if (params.getLength() >= 1) {
      klass = params.get("0");

      int index = klass.indexOf("@");
      if (index > 0) {
        project = klass.substring(index + 1);
        klass = klass.substring(0, index);
      } else {
        project = "SnipSnap";
      }
      if (params.getLength() == 2) {
        lineNumber = Integer.parseInt(params.get("1"));
      }
    } else {
      throw new IllegalArgumentException("xref macro needs one or two parameters");
    }

    XrefMapper.getInstance().expand(writer, klass, project, lineNumber);
    return;
  }
}
