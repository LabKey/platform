/*
 * This file is part of "SnipSnap Radeox Rendering Engine".
 *
 * Copyright (c) 2002 Stephan J. Schmidt, Matthias L. Jugel
 * All Rights Reserved.
 *
 * Please visit http://radeox.org/ for updates and contact.
 *
 * Modified 6/30/2011 by Isaac Hodes
 * replacing newlines with <br /> in order to prevent the paragraph
 * filter from destroying the layout of a code block
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
import org.radeox.filter.context.BaseFilterContext;
import org.radeox.filter.context.FilterContext;
import org.radeox.macro.code.SourceCodeFormatter;
import org.radeox.macro.parameter.MacroParameter;
import org.radeox.util.Service;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/*
 * Macro for displaying programming language source code. CodeMacro knows about
 * different source code formatters which can be plugged into radeox to
 * display more languages. CodeMacro displays Java, Ruby or SQL code.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: CodeMacro.java,v 1.14 2004/01/30 08:42:57 stephan Exp $
 */

public class CodeMacro extends LocalePreserved {
  private static Log log = LogFactory.getLog(CodeMacro.class);

  private Map formatters;
  private FilterContext nullContext = new BaseFilterContext();

  private String start;
  private String end;

  private String[] paramDescription =
      {"?1: syntax highlighter to use, defaults to java. Options include none, sql, xml, and java"};

  public String[] getParamDescription() {
    return paramDescription;
  }

  public String getLocaleKey() {
    return "macro.code";
  }

  public void setInitialContext(InitialRenderContext context) {
    super.setInitialContext(context);
    Locale outputLocale = (Locale) context.get(RenderContext.OUTPUT_LOCALE);
    String outputName = (String) context.get(RenderContext.OUTPUT_BUNDLE_NAME);
    ResourceBundle outputMessages = ResourceBundle.getBundle(outputName, outputLocale);

    start = outputMessages.getString(getLocaleKey() + ".start");
    end = outputMessages.getString(getLocaleKey() + ".end");
  }

  public CodeMacro() {
      formatters = new HashMap();

      Iterator formatterIt = Service.providers(SourceCodeFormatter.class);
    while (formatterIt.hasNext()) {
      try {
        SourceCodeFormatter formatter = (SourceCodeFormatter) formatterIt.next();
        String name = formatter.getName();
        if (formatters.containsKey(name)) {
          SourceCodeFormatter existing = (SourceCodeFormatter) formatters.get(name);
          if (existing.getPriority() < formatter.getPriority()) {
            formatters.put(name, formatter);
            log.debug("Replacing formatter: " + formatter.getClass() + " (" + name + ")");
          }
        } else {
          formatters.put(name, formatter);
          log.debug("Loaded formatter: " + formatter.getClass() + " (" + name +")");
        }
      } catch (Exception e) {
        log.warn("CodeMacro: unable to load code formatter", e);
      }
    }

      addSpecial('[');
      addSpecial(']');
      addSpecial('{');
      addSpecial('}');
      addSpecial('*');
      addSpecial('-');
      addSpecial('\\');
      addSpecial("\n", "<br />");
  }

  public void execute(Writer writer, MacroParameter params)
      throws IllegalArgumentException, IOException {

    SourceCodeFormatter formatter = null;

    if (params.getLength() == 0 || !formatters.containsKey(params.get("0"))) {
      formatter = (SourceCodeFormatter) formatters.get(initialContext.get(RenderContext.DEFAULT_FORMATTER));
      if (null == formatter) {
        System.err.println("Formatter not found.");
        formatter = (SourceCodeFormatter) formatters.get("java");
      }
    } else {
      formatter = (SourceCodeFormatter) formatters.get(params.get("0"));
    }

    String result = formatter.filter(params.getContent(), nullContext);

    writer.write(start);
    writer.write(replace(result.trim()));
    writer.write(end);
    return;
  }
}
