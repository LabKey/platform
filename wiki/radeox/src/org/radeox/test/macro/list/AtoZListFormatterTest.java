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
package org.radeox.test.macro.list;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.radeox.macro.list.SimpleList;
import org.radeox.macro.list.AtoZListFormatter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class AtoZListFormatterTest extends ListFormatterSupport {
  public AtoZListFormatterTest(String name) {
    super(name);
  }

  public static Test suite() {
    return new TestSuite(AtoZListFormatterTest.class);
  }

  protected void setUp() throws Exception {
    super.setUp();
    formatter = new AtoZListFormatter();
  }

  public void testSingeItem() {
//    Collection c = Arrays.asList(new String[]{"test"});
//    try {
//      formatter.format(writer, emptyLinkable, "", c, "", false);
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//    assertEquals("Single item is rendered",
//        "<table width=\"100%\" class=\"index-top\" cellpadding=\"0\" cellspacing=\"0\" border=" +
//        "\"0\"><tr><th><b> &nbsp;<a href=\"#idxA\">A</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxB\">B</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a h" +
//        "ref=\"#idxC\">C</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxD\">D</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxE\">E</a></b></th>" +
//        "<th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxF\">F</a></b></th><th>...</th><th>0&nbsp; </th></tr><tr><th><b> &nbsp;<a href=\"#idxG\">G</a></b></th><th>...</th><th>0" +
//        "&nbsp; </th><th><b> &nbsp;<a href=\"#idxH\">H</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxI\">I</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;" +
//        "<a href=\"#idxJ\">J</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxK\">K</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxL\">L</a></b><" +
//        "/th><th>...</th><th>0&nbsp; </th></tr><tr><th><b> &nbsp;<a href=\"#idxM\">M</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxN\">N</a></b></th><th>...</th><" +
//        "th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxO\">O</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxP\">P</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &n" +
//        "bsp;<a href=\"#idxQ\">Q</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxR\">R</a></b></th><th>...</th><th>0&nbsp; </th></tr><tr><th><b> &nbsp;<a href=\"#idx" +
//        "S\">S</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxT\">T</a></b></th><th>...</th><th>1&nbsp; </th><th><b> &nbsp;<a href=\"#idxU\">U</a></b></th><th>...</" +
//        "th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxV\">V</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxW\">W</a></b></th><th>...</th><th>0&nbsp; </th><th><b" +
//        "> &nbsp;<a href=\"#idxX\">X</a></b></th><th>...</th><th>0&nbsp; </th></tr><tr><th><b> &nbsp;<a href=\"#idxY\">Y</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"" +
//        "#idxZ\">Z</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idx0-9\">0-9</a></b></th><th>...</th><th>0&nbsp; </th><th><b> &nbsp;<a href=\"#idxAT\">@</a></b></th>" +
//        "<th>...</th><th>0&nbsp; </th><th></th><th></th><th></th><th></th><th></th><th></th><th></th><th></th></tr></table><div class=\"list-title\"></div><table width=\"100%\" class=\"i" +
//        "ndex\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><th><b><a name=\"idxA\"></a>A</b></th><th> </th><th><b><a name=\"idxA\"></a>B</b></th></tr><tr><th><b><a name=\"idxC\"></a>C<" +
//        "/b></th><th> </th><th><b><a name=\"idxC\"></a>D</b></th></tr><tr><th><b><a name=\"idxE\"></a>E</b></th><th> </th><th><b><a name=\"idxE\"></a>F</b></th></tr><tr><th><b><a name=\"id" +
//        "xG\"></a>G</b></th><th> </th><th><b><a name=\"idxG\"></a>H</b></th></tr><tr><th><b><a name=\"idxI\"></a>I</b></th><th> </th><th><b><a name=\"idxI\"></a>J</b></th></tr><tr><th><b><" +
//        "a name=\"idxK\"></a>K</b></th><th> </th><th><b><a name=\"idxK\"></a>L</b></th></tr><tr><th><b><a name=\"idxM\"></a>M</b></th><th> </th><th><b><a name=\"idxM\"></a>N</b></th></tr><t" +
//        "r><th><b><a name=\"idxO\"></a>O</b></th><th> </th><th><b><a name=\"idxO\"></a>P</b></th></tr><tr><th><b><a name=\"idxQ\"></a>Q</b></th><th> </th><th><b><a name=\"idxQ\"></a>R</b></" +
//        "th></tr><tr><th><b><a name=\"idxS\"></a>S</b></th><th> </th><th><b><a name=\"idxS\"></a>T</b></th></tr><tr><td></td><td> </td><td>test</td></tr><tr><th><b><a name=\"idxU\"></a>U<" +
//        "/b></th><th> </th><th><b><a name=\"idxU\"></a>V</b></th></tr><tr><th><b><a name=\"idxW\"></a>W</b></th><th> </th><th><b><a name=\"idxW\"></a>X</b></th></tr><tr><th><b><a name=\"id" +
//        "xY\"></a>Y</b></th><th> </th><th><b><a name=\"idxY\"></a>Z</b></th></tr><tr><th><b><a name=\"idx0-9\"></a>0-9</b></th><th> </th><th><b><a name=\"idx0-9\"></a>@</b></th></tr></table>",
//        writer.toString());
  }
}
