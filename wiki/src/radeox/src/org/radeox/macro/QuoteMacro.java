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
import org.radeox.macro.parameter.MacroParameter;

import java.io.IOException;
import java.io.Writer;

/*
 * Macro to display quotations from other sources. The
 * output is wrapped usually in <blockquote> to look like
 * a quotation.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: QuoteMacro.java,v 1.9 2004/01/15 13:04:35 stephan Exp $
 */

public class QuoteMacro extends LocalePreserved {
  private static Log log = LogFactory.getLog(QuoteMacro.class);

  private String[] paramDescription =
      {"?1: source",
       "?2: displayed description, default is Source"};

  public String[] getParamDescription() {
    return paramDescription;
  }

  public QuoteMacro() {
  }

  public String getLocaleKey() {
    return "macro.quote";
  }
  public void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException {

    writer.write("<blockquote class=\"quote\">");
    writer.write(params.getContent());
    String source = "Source"; // i18n
    if (params.getLength() == 2) {
      source = params.get(1);
    }
    // if more than one was present, we
    // should show a description for the link
    if (params.getLength() > 0) {
      writer.write("<a href=\""+params.get(0)+"\">");
      writer.write(source);
      writer.write("</a>");
    }
    writer.write("</blockquote>");
    return;
  }
}
