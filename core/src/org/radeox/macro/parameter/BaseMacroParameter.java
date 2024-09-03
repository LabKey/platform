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

package org.radeox.macro.parameter;

import org.radeox.api.engine.context.RenderContext;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 *
 * @author
 * @version $Id: BaseMacroParameter.java,v 1.10 2004/01/20 12:07:53 stephan Exp $
 */

public class BaseMacroParameter implements MacroParameter {
  private String content;
  protected Map params;
  private int size;
  protected RenderContext context;
  private int start;
  private int end;
  private int contentStart;
  private int contentEnd;

  public BaseMacroParameter() {
  }

  public BaseMacroParameter(RenderContext context) {
    this.context = context;
  }

  @Override
  public void setParams(String stringParams) {
    params = split(stringParams, "|");
    size = params.size();
  }

  @Override
  public RenderContext getContext() {
    return context;
  }

  @Override
  public Map getParams() {
    return params;
  }

  @Override
  public String getContent() {
    return content;
  }

  @Override
  public void setContent(String content) {
    this.content = content;
  }

  @Override
  public int getLength() {
    return size;
  }

  @Override
  public String get(String index, int idx) {
    String result = get(index);
    if (result == null) {
      result = get(idx);
    }
    return result;
  }

  @Override
  public String get(String index) {
    return (String) params.get(index);
  }

  @Override
  public String get(int index) {
    return get("" + index);
  }

  /**
   *
   * Splits a String on a delimiter to a List. The function works like
   * the perl-function split.
   *
   * @param aString    a String to split
   * @param delimiter  a delimiter dividing the entries
   * @return           a Array of splittet Strings
   */

  private Map split(String aString, String delimiter) {
    Map result = new HashMap();

    if (null != aString) {
      StringTokenizer st = new StringTokenizer(aString, delimiter);
      int i = 0;

      while (st.hasMoreTokens()) {
        String value = st.nextToken();
        String key = "" + i;
        if (value.indexOf("=") != -1) {
          // Store this for
          result.put(key, insertValue(value));
          int index = value.indexOf("=");
          key = value.substring(0, index);
          value = value.substring(index + 1);

          result.put(key, insertValue(value));
        } else {
          result.put(key, insertValue(value));
        }
        i++;
      }
    }
    return result;
  }

  private String insertValue(String s) {
    int idx = s.indexOf('$');
    if (idx != -1) {
      StringBuffer tmp = new StringBuffer();
      Map globals = context.getParameters();
      String var = s.substring(idx + 1);
      if (idx > 0) tmp.append(s.substring(0, idx));
      if (globals.containsKey(var)) {
        tmp.append(globals.get(var));
      }
      return tmp.toString();
    }
    return s;
  }

  @Override
  public void setStart(int start) {
    this.start = start;
  }

  @Override
  public void setEnd(int end) {
    this.end = end;
  }

  @Override
  public int getStart() {
    return this.start;
  }

  @Override
  public int getEnd() {
    return this.end;
  }

  @Override
  public int getContentStart() {
    return contentStart;
  }

  @Override
  public void setContentStart(int contentStart) {
    this.contentStart = contentStart;
  }

  @Override
  public int getContentEnd() {
    return contentEnd;
  }

  @Override
  public void setContentEnd(int contentEnd) {
    this.contentEnd = contentEnd;
  }

}
