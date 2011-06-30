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

package org.radeox.util.logging;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *  Logger for logging events. Logger uses al LoggerImplementation for
 *  logging concrete logging, e.g. SystemOutLogger
 *
 * @author stephan
 * @version $Id: Logger.java,v 1.7 2003/05/23 10:47:26 stephan Exp $
 */

public class Logger {
  // private static LogHandler handler = new ApplicationLogger();
  private static LogHandler handler = new SystemErrLogger();

  public final static boolean PRINT_STACKTRACE = true;

  public final static DateFormat format = new SimpleDateFormat("hh:mm:ss.SSS");
  public final static int ALL = -1;
  public final static int NONE = 4;
  public final static int PERF = 0;
  public final static int DEBUG = 1;
  public final static int WARN = 2;
  public final static int FATAL = 3;
  public final static int LEVEL = ALL;

  public final static String[] levels = {"PERF  ", "DEBUG ", "WARN  ", "FATAL "};

  public static void log(String output) {
    if (LEVEL <= DEBUG) log(Logger.DEBUG, output);
  }

  public static void perf(String output) {
    if (LEVEL <= PERF) log(Logger.PERF, output);
  }

  public static void debug(String output) {
    if (LEVEL <= DEBUG) log(Logger.DEBUG, output);
  }

  public static void warn(String output) {
    if (LEVEL <= WARN) log(Logger.WARN, output);
  }

  public static void warn(String output, Throwable e) {
    if (LEVEL <= WARN) log(Logger.WARN, output, e);
  }

  public static void fatal(String output) {
    if (LEVEL <= FATAL) log(Logger.WARN, output);
  }

  public static void fatal(String output, Exception e) {
    if (LEVEL <= FATAL) log(Logger.WARN, output, e);
  }

  public static void log(String output, Exception e) {
    if (LEVEL <= DEBUG) log(Logger.DEBUG, output, e);
  }

  public static void log(int level, String output) {
    handler.log(format.format(new Date())  + " " + levels[level] + " - " + output);
  }

  public static void log(int level, String output, Throwable e) {
    handler.log(format.format(new Date()) + " " + levels[level] + " - " + output + ": " + e.getMessage(), e);
  }

  public static void setHandler(LogHandler handler) {
    Logger.handler = handler;
  }
}
