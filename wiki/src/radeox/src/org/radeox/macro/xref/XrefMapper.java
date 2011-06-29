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

package org.radeox.macro.xref;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Stores information and links to xref Java source code
 * e.g.
 * http://nanning.sourceforge.net/xref/com/tirsen/nanning/MixinInstance.html#83
 *
 * @author Stephan J. Schmidt
 * @version $Id: XrefMapper.java,v 1.3 2003/06/11 10:04:27 stephan Exp $
 */

public class XrefMapper {
  private static Log log = LogFactory.getLog(XrefMapper.class);

  private final static String FILENAME = "conf/xref.txt";

  private static XrefMapper instance;
  private Map xrefMap;

  public static synchronized XrefMapper getInstance() {
    if (null == instance) {
      instance = new XrefMapper();
    }
    return instance;
  }

  public XrefMapper() {
    xrefMap = new HashMap();

    boolean fileNotFound = false;
    try {
      BufferedReader br = new BufferedReader(
          new InputStreamReader(
              new FileInputStream(FILENAME)));
      addXref(br);
    } catch (IOException e) {
      log.warn("Unable to read " + FILENAME);
      fileNotFound = true;
    }

    if (fileNotFound) {
      BufferedReader br = null;
      try {
        br = new BufferedReader(
            new InputStreamReader(
                XrefMapper.class.getResourceAsStream("/" + FILENAME)
            )
        );
        addXref(br);
      } catch (Exception e) {
        log.warn("Unable to read " + FILENAME);
      }
    }
  }

  public void addXref(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      StringTokenizer tokenizer = new StringTokenizer(line, " ");
      String site = tokenizer.nextToken();
      String baseUrl = tokenizer.nextToken();
      xrefMap.put(site.toLowerCase(), baseUrl);
    }
  }

  public boolean contains(String external) {
    return xrefMap.containsKey(external);
  }

  public Writer expand(Writer writer, String className, String site, int lineNumber) throws IOException {
    site = site.toLowerCase();
    if (xrefMap.containsKey(site)) {
      writer.write("<a href=\"");
      writer.write((String) xrefMap.get(site));
      writer.write("/");
      writer.write(className.replace('.', '/'));
      writer.write(".html");
      if (lineNumber > 0) {
        writer.write("#");
        writer.write("" + lineNumber);
      }
      writer.write("\">");
      writer.write(className);
      writer.write("</a>");
    } else {
      log.debug("Xrefs : " + xrefMap);
      log.warn(site + " not found");
    }
    return writer;
  }

  public Writer appendTo(Writer writer) throws IOException {
    writer.write("{table}\n");
    writer.write("Binding|Site\n");
    Iterator iterator = xrefMap.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry entry = (Map.Entry) iterator.next();
      writer.write((String) entry.getKey());
      writer.write("|");
      writer.write((String) entry.getValue());
      writer.write("\n");
    }
    writer.write("{table}");
    return writer;
  }

}
