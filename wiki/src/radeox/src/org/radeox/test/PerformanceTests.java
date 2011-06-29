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
package org.radeox.test;

import com.clarkware.junitperf.TimedTest;
import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.radeox.EngineManager;
import org.radeox.engine.context.BaseRenderContext;
import org.radeox.util.logging.Logger;
import org.radeox.util.logging.NullLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class PerformanceTests {
  public static void main(String[] args) throws IOException {
    TestRunner.run(suite());
  }

  public static Test suite() throws IOException {
    // get test markup from text file
    File wikiTxt = new File("wiki.txt");
    BufferedReader reader = new BufferedReader(new FileReader(wikiTxt.getCanonicalFile()));
    StringBuffer input = new StringBuffer();
    String tmp;
    while ((tmp = reader.readLine()) != null) {
      input.append(tmp);
    }
    Logger.setHandler(new NullLogger());
    System.err.println(EngineManager.getInstance().render("__initialized__", new BaseRenderContext()));

    TestSuite s = new TestSuite();
    long maxElapsedTime = 30 * 1000; // 30s
    StringBuffer testString = new StringBuffer();
    for (int i = 0; i < 10; i++) {
      testString.append(input);
      Test renderEngineTest = new RenderEnginePerformanceTest(testString.toString());
      Test timedTest = new TimedTest(renderEngineTest, maxElapsedTime, false);
      s.addTest(timedTest);
    }
    return s;
  }


}