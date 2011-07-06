/*
 * This file is part of "SnipSnap Radeox Rendering Engine".
 *
 * Copyright (c) 2002 Stephan J. Schmidt, Matthias L. Jugel
 * All Rights Reserved.
 *
 * Please visit http://radeox.org/ for updates and contact.
 *
 * Modifed by Isaac Hodes on 7/5/2011
 * added the style attr
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

import org.radeox.api.engine.ImageRenderEngine;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.filter.EscapeFilter;
import org.radeox.macro.parameter.MacroParameter;
import org.radeox.util.Encoder;

import java.io.IOException;
import java.io.Writer;

/*
 * Macro for displaying external links with a name. The normal UrlFilter
 * takes the url as a name.
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: LinkMacro.java,v 1.16 2004/03/11 20:06:23 leo Exp $
 */

public class LinkMacro extends BaseLocaleMacro {
    public String getLocaleKey() {
        return "macro.link";
    }

    public void execute(Writer writer, MacroParameter params)
            throws IllegalArgumentException, IOException {

        RenderContext context = params.getContext();
        RenderEngine engine = context.getRenderEngine();

        String text = params.get("text", 0);
        String url = params.get("url", 1);
        String img = params.get("img", 2);
        String style = params.get("style", 3);


        // check for single url argument (text == url)
        if(params.getLength() == 1) {
            url = text;
            text = Encoder.toEntity(text.charAt(0)) + Encoder.escape(text.substring(1));
        }

        if (url != null && text != null) {
            writer.write("<span class=\"nobr\"");
            if(style != null)
                writer.write(" style=\"" + style + "\"");
            writer.write(">");
            if (!"none".equals(img) && engine instanceof ImageRenderEngine) {
                writer.write(((ImageRenderEngine) engine).getExternalImageLink());
            }
            writer.write("<a href=\"");
            writer.write(url);
            writer.write("\">");
            writer.write(text);
            writer.write("</a></span>");
        } else {
            throw new IllegalArgumentException("link needs a name and a url as argument");
        }
        return;
    }
}
