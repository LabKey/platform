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

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/*
 * MacroListMacro displays a list of all known macros of the EngineManager
 * with their name, parameters and a description.
 *
 * @author Matthias L. Jugel
 * @version $Id: MacroListMacro.java,v 1.9 2003/08/21 08:50:07 stephan Exp $
 */

public class MacroListMacro extends BaseLocaleMacro {
  public String getLocaleKey() {
    return "macro.macrolist";
  }

  public void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException {
    if (params.getLength() == 0) {
      appendTo(writer);
    } else {
      throw new IllegalArgumentException("MacroListMacro: number of arguments does not match");
    }
  }

  public Writer appendTo(Writer writer) throws IOException {
    List macroList = MacroRepository.getInstance().getPlugins();
    Collections.sort(macroList);
    Iterator iterator = macroList.iterator();
    writer.write("{table}\n");
    writer.write("Macro|Description|Parameters\n");
    while (iterator.hasNext()) {
      Macro macro = (Macro) iterator.next();
      writer.write(macro.getName());
      writer.write("|");
      writer.write(macro.getDescription());
      writer.write("|");
      String[] params = macro.getParamDescription();
      if (params.length == 0) {
        writer.write("none");
      } else {
        for (int i = 0; i < params.length; i++) {
          String description = params[i];
          if (description.startsWith("?")) {
            writer.write(description.substring(1));
            writer.write(" (optional)");
          } else {
            writer.write(params[i]);
          }
          writer.write("\\\\");
        }
      }
      writer.write("\n");
    }
    writer.write("{table}");
    return writer;
  }

}
