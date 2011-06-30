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
package org.radeox.test.macro;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.EngineManager;

public class TableMacroTest extends MacroTestSupport {
  public TableMacroTest(String name) {
    super(name);
  }

  public static Test suite() {
    return new TestSuite(TableMacroTest.class);
  }

  public void testTable() {
    String result = EngineManager.getInstance().render("{table}1|2\n3|4{table}", context);
    assertEquals("<table class=\"wiki-table\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th>1</th><th>2</th></tr><tr class=\"table-odd\"><td>3</td><td>4</td></tr></table>", result);
  }

  public void testEmptyHeader() {
    String result = EngineManager.getInstance().render("{table}|\n3|4{table}", context);
    assertEquals("<table class=\"wiki-table\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th>&#160;</th><th>&#160;</th></tr><tr class=\"table-odd\"><td>3</td><td>4</td></tr></table>", result);
  }

  public void testMultiTable() {
    String result = EngineManager.getInstance().render("{table}1|2\n3|4{table}\n{table}5|6\n7|8{table}", context);
    assertEquals("<table class=\"wiki-table\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th>1</th><th>2</th></tr><tr class=\"table-odd\"><td>3</td><td>4</td></tr></table>\n"+
                 "<table class=\"wiki-table\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th>5</th><th>6</th></tr><tr class=\"table-odd\"><td>7</td><td>8</td></tr></table>", result);
  }

  public void testCalcIntSum() {
    String result = EngineManager.getInstance().render("{table}1|2\n3|=SUM(A1:A2){table}", context);
    assertEquals("<table class=\"wiki-table\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th>1</th><th>2</th></tr><tr class=\"table-odd\"><td>3</td><td>4</td></tr></table>", result);
  }

  public void testCalcFloatSum() {
    String result = EngineManager.getInstance().render("{table}1|2\n3.0|=SUM(A1:A2){table}", context);
    assertEquals("<table class=\"wiki-table\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th>1</th><th>2</th></tr><tr class=\"table-odd\"><td>3.0</td><td>4.0</td></tr></table>", result);
  }

  public void testFloatAvg() {
    String result = EngineManager.getInstance().render("{table}1|2\n4|=AVG(A1:A2){table}", context);
    assertEquals("<table class=\"wiki-table\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th>1</th><th>2</th></tr><tr class=\"table-odd\"><td>4</td><td>2.5</td></tr></table>", result);
  }

}
