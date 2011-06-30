/*
 * This file is part of "Radeox".
 *
 * Copyright (c) 2003 Stephan J. Schmidt
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

package radeox.test.groovy;

import groovy.text.Template;
import groovy.text.TemplateEngine;
import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

import org.radeox.example.RadeoxTemplateEngine;

public class RadeoxTemplateEngineTest extends TestCase {

  public RadeoxTemplateEngineTest(String name) {
    super(name);
  }

  public void testRadeoxTemplate() {
    String text = "__Dear__ ${firstname}";

    Map binding = new HashMap();
    binding.put("firstname", "stephan");

    TemplateEngine engine = new RadeoxTemplateEngine();
    Template template = null;
    try {
      template = engine.createTemplate(text);
    } catch (Exception e) {
      e.printStackTrace();
    }
    template.setBinding(binding);

    String result = "<b class=\"bold\">Dear</b> stephan";
    assertEquals(result, template.toString());
  }
}
