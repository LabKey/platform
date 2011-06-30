/*
 * This file is part of "SnipSnap Radeox Rendering Engine".
 *
 * Copyright (c) 2002-2004 Stephan J. Schmidt, Matthias L. Jugel
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

import java.util.Map;

/**
 * Encapsulates parameters for an executed Macro call
 *
 * @author Stephan J. Schmidt
 * @version $Id: MacroParameter.java,v 1.10 2004/01/20 12:07:53 stephan Exp $
 */

public interface MacroParameter {
  public void setParams(String stringParams);

  public String getContent();

  public void setContent(String content);

  public int getLength();

  public String get(String index, int idx);

  public String get(String index);

  public String get(int index);

  public Map getParams();

  public void setStart(int start);

  public void setEnd(int end);

  public int getStart();

  public int getEnd();

  public void setContentStart(int start);

  public void setContentEnd(int end);

  public int getContentStart();

  public int getContentEnd();

  public RenderContext getContext();
}
