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

import org.radeox.api.engine.ImageRenderEngine;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.LocaleRegexTokenFilter;
import org.radeox.regex.MatchResult;
import org.radeox.util.Encoder;

import java.text.MessageFormat;

/*
 * UrlFilter finds http:// style URLs in its input and transforms this
 * to <a href="url">url</a>
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: UrlFilter.java,v 1.18 2004/04/15 13:56:14 stephan Exp $
 */

public class UrlFilter extends LocaleRegexTokenFilter implements CacheFilter {
  private MessageFormat formatter;

  protected String getLocaleKey() {
     return "filter.url";
  }

  public void setInitialContext(InitialRenderContext context) {
    super.setInitialContext(context);
    String outputTemplate = outputMessages.getString(getLocaleKey() + ".print");
    formatter = new MessageFormat("");
    formatter.applyPattern(outputTemplate);
  }

  public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
    buffer.append(result.group(1));
    // Does our engine know images?
    RenderEngine engine = context.getRenderContext().getRenderEngine();
    String externalImage = "";
    if (engine instanceof ImageRenderEngine) {
      buffer.append(((ImageRenderEngine) engine).getExternalImageLink());
    }

    buffer.append(formatter.format(new Object[]{externalImage,
                                                Encoder.escape(result.group(2)),
                                                Encoder.toEntity(result.group(2).charAt(0)) + result.group(2).substring(1)}));
    return;
  }
}
