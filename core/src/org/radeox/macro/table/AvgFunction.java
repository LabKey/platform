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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * A function that calculates the average  table cells
 *
 * @author stephan
 * @version $Id: AvgFunction.java,v 1.2 2003/06/11 10:04:27 stephan Exp $
 */

public class AvgFunction implements Function {
  private static Logger log = LogManager.getLogger(AvgFunction.class);


  @Override
  public String getName() {
    return "AVG";
  }

  @Override
  public void execute(Table table, int posx, int posy, int startX, int startY, int endX, int endY) {
    float sum = 0;
    int count = 0;
    for (int x = startX; x <= endX; x++) {
      for (int y = startY; y <= endY; y++) {
        //Logger.debug("x="+x+" y="+y+" >"+getXY(x,y));
        try {
          sum += Integer.parseInt((String) table.getXY(x, y));
          count++;
        } catch (Exception e) {
          try {
            sum += Float.parseFloat((String) table.getXY(x, y));
            count++;
          } catch (NumberFormatException e1) {
            log.debug("SumFunction: unable to parse " + table.getXY(x, y));
          }
        }
      }
    }
    //Logger.debug("Sum="+sum);
    table.setXY(posx, posy, "" + sum / count);
  }

}
