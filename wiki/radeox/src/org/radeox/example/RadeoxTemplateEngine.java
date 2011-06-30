/*
 * Copyright (c) 2004 Stephan J. Schmidt
 * All Rights Reserved.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * --LICENSE NOTICE--
 */

package org.radeox.example;

import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;
import groovy.text.TemplateEngine;
import org.codehaus.groovy.syntax.SyntaxException;
import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.engine.context.BaseRenderContext;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;


/**
 * Groovy Template Engine which uses Radeox to render text markup
 *
 * @author Stephan J. Schmidt
 * @version $Id: RadeoxTemplateEngine.java,v 1.1 2004/04/14 13:03:49 stephan Exp $
 */

public class RadeoxTemplateEngine extends TemplateEngine {
   public Template createTemplate(Reader reader) throws SyntaxException, ClassNotFoundException, IOException {
       RenderContext context = new BaseRenderContext();
       RenderEngine engine = new BaseRenderEngine();
       String renderedText = engine.render(reader , context);

       TemplateEngine templateEngine = new SimpleTemplateEngine();
       return templateEngine.createTemplate(new StringReader(renderedText));
   }
}