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

package org.radeox.macro.api;

import java.io.IOException;
import java.io.Writer;

/**
 * Converts a class name to an API url
 *
 * @author Stephan J. Schmidt
 * @version $Id: ApiConverter.java,v 1.4 2003/05/23 10:47:25 stephan Exp $
 */

public interface ApiConverter {
  /**
   * Converts a class name to an url and adds the url to an
   * Writer. The url usually shows som API information about
   * the class e.g. for Java classes this points to the API
   * documentation on the Sun site.
   *
   * @param writer Writer to add the class url to
   * @param className Namee of the class to create pointer for
   */
  public void appendUrl(Writer writer, String className) throws IOException ;

  /**
   * Set the base Url for the Converter. A converter
   * creates an API pointer by combining an base url
   * and the name of a class.
   *
   * @param baseUrl Url to use when creating an API pointer
   */
  public void setBaseUrl(String baseUrl);

  /**
   * Get the base Url for the Converter. A converter
   * creates an API pointer by combining an base url
   * and the name of a class.
   *
   * @return baseUrl Url the converter uses when creating an API pointer
   */
  public String getBaseUrl();

  /**
   * Returns the name of the converter. This is used to configure
   * the BaseUrls and associate them with a concrete converter.
   *
   * @return name Name of the Converter, e.g. Java12
   */
  public String getName();
}
