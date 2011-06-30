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

/*
 * Macro to easily link to RFCs. The website to link to
 * is currently hard wired.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: RfcMacro.java,v 1.6 2003/08/21 08:50:07 stephan Exp $
 */

public class RfcMacro extends BaseLocaleMacro {

  public String getLocaleKey() {
    return "macro.rfc";
  }

  public void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException {

    if (params.getLength() == 1) {
      String number = params.get("0");
      String view = "RFC" + number;
      // ftp://ftp.rfc-editor.org/in-notes/rfc3300.txt
      // http://zvon.org/tmRFC/RFC3300/Output/index.html
      appendRfc(writer, number, view);
      return;
    } else if (params.getLength() == 2) {
      String number = params.get(0);
      String view = params.get(1);
      appendRfc(writer, number, view);
    } else {
      throw new IllegalArgumentException("needs an RFC numer as argument");
    }
  }

  public void appendRfc(Writer writer, String number, String view) throws IOException, IllegalArgumentException {
    //writer.write("<a href=\"ftp://ftp.rfc-editor.org/in-notes/rfc");
    try {

      Integer dummy = Integer.getInteger(number);
    } catch (Exception e) {
      throw new IllegalArgumentException();
    }
    writer.write("<a href=\"http://zvon.org/tmRFC/RFC");
    writer.write(number);
    writer.write("/Output/index.html\">");
    writer.write(view);
    writer.write("</a>");
    return;
  }
}
