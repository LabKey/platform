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

package org.radeox.filter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.util.PageFlowUtil;
import org.radeox.api.engine.IncludeRenderEngine;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;
import org.radeox.macro.Macro;
import org.radeox.macro.MacroRepository;
import org.radeox.macro.parameter.MacroParameter;
import org.radeox.util.StringBufferWriter;

import java.io.Writer;

/*
 * Class that finds snippets (macros) like
 * {link:neotis|http://www.neotis.de} ---> <a href="....>
 * {!neotis} -> include neotis object, e.g. a wiki page
 *
 * Macros can built with a start and an end, e.g.
 * {code}
 *     ...
 * {code}
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: MacroFilter.java,v 1.18 2004/04/15 13:56:14 stephan Exp $
 */

public class MacroFilter extends RegexTokenFilter {
  private static Logger log = LogManager.getLogger(MacroFilter.class);

//  private static MacroFilter instance;

  // Map of known macros with name and macro object
  private MacroRepository macros;
//  private static Object monitor = new Object();
//  private static Object[] noArguments = new Object[]{};

  public MacroFilter() {
    // optimized by Jeffrey E.F. Friedl
    super("\\{([^:}]+)(?::([^\\}]*))?\\}(.*?)\\{\\1\\}", SINGLELINE);
    addRegex("\\{([^:}]+)(?::([^\\}]*))?\\}", "", MULTILINE);
 }

  @Override
  public void setInitialContext(InitialRenderContext context) {
    macros = MacroRepository.getInstance();
    macros.setInitialContext(context);
  }

  protected MacroRepository getMacroRepository() {
    return macros;
  }

  @Override
  public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
    String command = result.group(1);

    if (command != null) {
      // {$peng} are variables not macros.
      if (!command.startsWith("$")) {
        MacroParameter mParams = context.getMacroParameter();
        switch(result.groups()) {
          case 3:
            mParams.setContent(result.group(3));
            mParams.setContentStart(result.beginOffset(3));
            mParams.setContentEnd(result.endOffset(3));
          case 2: mParams.setParams(result.group(2));
        }
        mParams.setStart(result.beginOffset(0));
        mParams.setEnd(result.endOffset(0));

        // @DANGER: recursive calls may replace macros in included source code
        try {
          if (getMacroRepository().containsKey(command)) {
            Macro macro = (Macro) getMacroRepository().get(command);
            // recursively filter macros within macros
            if (null != mParams.getContent()) {
              mParams.setContent(filter(mParams.getContent(), context));
            }
            Writer writer = new StringBufferWriter(buffer);
            macro.execute(writer, mParams);
          } else if (command.startsWith("!")) {
            // @TODO including of other snips
            RenderEngine engine = context.getRenderContext().getRenderEngine();
            if (engine instanceof IncludeRenderEngine) {
              String include = ((IncludeRenderEngine) engine).include(command.substring(1));
              if (null != include) {
                // Filter paramFilter = new ParamFilter(mParams);
                // included = paramFilter.filter(included, null);
                buffer.append(include);
              } else {
                buffer.append(command.substring(1) + " not found.");
              }
            }
            return;
          } else {
            buffer.append(result.group(0));
            return;
          }
        } catch (IllegalArgumentException e) {
          buffer.append("<div class=\"error\">" + PageFlowUtil.filter(command + ": " + e.getMessage()) + "</div>");
        } catch (Throwable e) {
          log.warn("MacroFilter: unable to format macro: " + result.group(1) + " in " + context.getRenderContext(), e);
          buffer.append("<div class=\"error\">" + PageFlowUtil.filter(command + ": " + e.getMessage()) + "</div>");
          return;
        }
      } else {
        buffer.append("<");
        buffer.append(command.substring(1));
        buffer.append(">");
      }
    } else {
      buffer.append(result.group(0));
    }
  }
}
