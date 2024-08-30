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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.radeox.macro.parameter.MacroParameter;

import java.io.IOException;
import java.io.Writer;

/*
 * Displays a file path. This is used to store a filepath in an
 * OS independent way and then display the file path as needed.
 * This macro also solves the problems with to many backslashes
 * in Windows filepaths when they are entered in Snipsnap.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: FilePathMacro.java,v 1.8 2003/08/21 08:52:32 stephan Exp $
 */

public class FilePathMacro extends LocalePreserved {
  private static Logger log = LogManager.getLogger(FilePathMacro.class);

  private String[] paramDescription = {"1: file path"};

   @Override
   public String getLocaleKey() {
    return "macro.filepath";
  }

  public FilePathMacro() {
    addSpecial('\\');
  }

  @Override
  public String getDescription() {
    return "Displays a file system path. The file path should use slashes. Defaults to windows.";
  }

  @Override
  public String[] getParamDescription() {
    return paramDescription;
  }

  @Override
  public void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException {

    if (params.getLength() == 1) {
      String path = params.get("0").replace('/', '\\');
      writer.write(replace(path));
    }
    return;
  }
}
