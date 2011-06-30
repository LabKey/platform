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

package org.radeox.macro.table;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A function that summerizes table cells
 *
 * @author stephan
 * @version $Id: SumFunction.java,v 1.2 2003/06/11 10:04:27 stephan Exp $
 */

public class SumFunction implements Function {
  private static Log log = LogFactory.getLog(SumFunction.class);


  public String getName() {
    return "SUM";
  }

  public void execute(Table table, int posx, int posy, int startX, int startY, int endX, int endY) {
    float sum = 0;
    boolean floating = false;
    for (int x = startX; x <= endX; x++) {
      for (int y = startY; y <= endY; y++) {
        //Logger.debug("x="+x+" y="+y+" >"+getXY(x,y));
        try {
          sum += Integer.parseInt((String) table.getXY(x, y));
        } catch (Exception e) {
          try {
            sum += Float.parseFloat((String) table.getXY(x, y));
            floating = true;
          } catch (NumberFormatException e1) {
            log.debug("SumFunction: unable to parse " + table.getXY(x, y));
          }
        }
      }
    }
    //Logger.debug("Sum="+sum);
    if (floating) {
      table.setXY(posx, posy, "" + sum);
    } else {
      table.setXY(posx, posy, "" + (int) sum);
    }
  }

}
