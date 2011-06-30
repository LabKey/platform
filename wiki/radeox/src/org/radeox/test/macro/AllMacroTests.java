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
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.radeox.test.macro.code.AllCodeMacroTests;

public class AllMacroTests extends TestCase {
  public AllMacroTests(String name) {
    super(name);
  }

  public static Test suite() {
    TestSuite s = new TestSuite();
    s.addTestSuite(ApiMacroTest.class);
    s.addTestSuite(ApiDocMacroTest.class);
    s.addTestSuite(AsinMacroTest.class);
    s.addTestSuite(FilePathMacroTest.class);
    s.addTestSuite(IsbnMacroTest.class);
    s.addTestSuite(LinkMacroTest.class);
    s.addTestSuite(ParamMacroTest.class);
    s.addTestSuite(TableMacroTest.class);
    s.addTestSuite(XrefMacroTest.class);
    s.addTestSuite(MailToMacroTest.class);
    s.addTestSuite(RfcMacroTest.class);
//    s.addTestSuite(YipeeTest.class);

    s.addTest(AllCodeMacroTests.suite());
    return s;
  }
}
