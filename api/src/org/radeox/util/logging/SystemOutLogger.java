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


/**
 * Concrete Logger that logs to System Out
 *
 * @author stephan
 * @version $Id: SystemOutLogger.java,v 1.3 2003/03/25 10:37:54 leo Exp $
 */

public class SystemOutLogger implements LogHandler {
  @Override
  public void log(String output) {
    System.out.println(output);
  }

  @Override
  public void log(String output, Throwable e) {
    System.out.println(output);
    if (Logger.PRINT_STACKTRACE) e.printStackTrace(System.out);
  }

}
