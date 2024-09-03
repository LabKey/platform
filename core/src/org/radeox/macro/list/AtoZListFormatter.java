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
package org.radeox.macro.list;

import org.radeox.util.Linkable;
import org.radeox.util.Nameable;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Formats a list as AtoZ listing separated by the alphabetical characters.
 *
 * @author Matthias L. Jugel
 * @version $Id: AtoZListFormatter.java,v 1.4 2004/02/16 16:13:20 leo Exp $
 */
public class AtoZListFormatter implements ListFormatter {
  @Override
  public String getName() {
    return "atoz";
  }

  /**
   * Create an A to Z index
   */
  @Override
  public void format(Writer writer, Linkable current, String listComment, Collection c, String emptyText, boolean showSize)
      throws IOException {
    if (c.size() > 0) {
      Iterator it = c.iterator();
      Map atozMap = new HashMap();
      List numberRestList = new ArrayList();
      List otherRestList = new ArrayList();
      while (it.hasNext()) {
        Object object = it.next();
        String name, indexChar;
        if (object instanceof Nameable) {
          name = ((Nameable) object).getName();
        } else {
          name = object.toString();
        }
        indexChar = name.substring(0, 1).toUpperCase();
        if (object instanceof Linkable) {
          name = ((Linkable) object).getLink();
        }

        if (indexChar.charAt(0) >= 'A' && indexChar.charAt(0) <= 'Z') {
          if (!atozMap.containsKey(indexChar)) {
            atozMap.put(indexChar, new ArrayList());
          }
          List list = (List) atozMap.get(indexChar);
          list.add(name);
        } else if (indexChar.charAt(0) >= '0' && indexChar.charAt(0) <= '9') {
          numberRestList.add(name);
        } else {
          otherRestList.add(name);
        }
      }

      writer.write("<table width=\"100%\" class=\"index-top\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
      for (int idxChar = 'A'; idxChar <= 'Z';) {
        writer.write("<tr>");
        for (int i = 0; i < 6 && idxChar + i <= 'Z'; i++) {
          String ch = "" + (char) (idxChar + i);
          writer.write("<th><b> &nbsp;<a href=\"");
          writer.write(current.getLink());
          writer.write("#idx" + ch + "\">");
          writer.write(ch);
          writer.write("</a></b></th>");
          writer.write("<th>...</th><th>");
          writer.write("" + (atozMap.get(ch) == null ? 0 : ((List) atozMap.get(ch)).size()));
          writer.write("&nbsp; </th>");
        }
        idxChar += 6;
        if (idxChar >= 'Z') {
          writer.write("<th><b> &nbsp;<a href=\"");
          writer.write(current.getLink());
          writer.write("#idx0-9\">0-9</a></b></th>");
          writer.write("<th>...</th><th>");
          writer.write("" + numberRestList.size());
          writer.write("&nbsp; </th>");
          writer.write("<th><b> &nbsp;<a href=\"");
          writer.write(current.getLink());
          writer.write("#idxAT\">@</a></b></th>");
          writer.write("<th>...</th><th>");
          writer.write("" + otherRestList.size());
          writer.write("&nbsp; </th>");
          writer.write("<th></th><th></th><th></th><th></th>");
          writer.write("<th></th><th></th><th></th><th></th>");
        }
        writer.write("</tr>");

      }
      writer.write("</table>");

      writer.write("<div class=\"list-title\">");
      writer.write(listComment);
      if (showSize) {
        writer.write(" (");
        writer.write("" + c.size());
        writer.write(")");
      }
      writer.write("</div>");
      writer.write("<table width=\"100%\" class=\"index\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
      for (int ch = 'A'; ch <= 'Z'; ch += 2) {
        String left = "" + (char) ch;
        String right = "" + (char) (ch + 1);

        insertCharHeader(writer, left, right);
        addRows(writer, (List) atozMap.get(left), (List) atozMap.get(right));
      }
      insertCharHeader(writer, "0-9", "@");
      addRows(writer, numberRestList, otherRestList);
      writer.write("</table>");
    } else {
      writer.write(emptyText);
    }
  }

  private void addRows(Writer writer, List listLeft, List listRight) throws IOException {
    Iterator leftIt = listLeft != null ? listLeft.iterator() : new EmptyIterator();
    Iterator rightIt = listRight != null ? listRight.iterator() : new EmptyIterator();

    while (leftIt.hasNext() || rightIt.hasNext()) {
      String leftName = (String) (leftIt != null && leftIt.hasNext() ? leftIt.next() : null);
      String rightName = (String) (rightIt != null && rightIt.hasNext() ? rightIt.next() : null);
      insertRow(writer, leftName, rightName, false);
    }
  }

  private void insertCharHeader(Writer writer, String leftHeader, String rightHeader) throws IOException {
    writer.write("<tr><th>");
    writer.write("<b><a name=\"idx");
    writer.write("@".equals(leftHeader) ? "AT" : leftHeader);
    writer.write("\"></a>");
    writer.write(leftHeader);
    writer.write("</b></th><th> </th><th>");
    writer.write("<b><a name=\"idx");
    writer.write("@".equals(rightHeader) ? "AT" : rightHeader);
    writer.write("\"></a>");
    writer.write(rightHeader);
    writer.write("</b></th></tr>");
  }

  private void insertRow(Writer writer, String left, String right, boolean odd) throws IOException {
    writer.write("<tr><td>");
    if (left != null) {
      writer.write(left);
    }
    writer.write("</td><td> </td><td>");
    if (right != null) {
      writer.write(right);
    }
    writer.write("</td></tr>");
  }

  private class EmptyIterator implements Iterator {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Object next() {
      return null;
    }

    @Override
    public void remove() {
    }
  }
}
