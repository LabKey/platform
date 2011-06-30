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

package org.radeox.filter.interwiki;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radeox.util.Encoder;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Stores information and links to other wikis forming a
 * InterWiki
 *
 * @author Stephan J. Schmidt
 * @version $Id: InterWiki.java,v 1.6 2004/01/19 11:45:24 stephan Exp $
 */

public class InterWiki {
  private static Log log = LogFactory.getLog(InterWiki.class);

  private static InterWiki instance;
  private Map interWiki;

  public static synchronized InterWiki getInstance() {
    if (null == instance) {
      instance = new InterWiki();
    }
    return instance;
  }

  public InterWiki() {
    interWiki = new HashMap();
    interWiki.put("LCOM", "http://www.langreiter.com/space/");
    interWiki.put("ESA", "http://earl.strain.at/space/");
    interWiki.put("C2", "http://www.c2.com/cgi/wiki?");
    interWiki.put("WeblogKitchen", "http://www.weblogkitchen.com/wiki.cgi?");
    interWiki.put("Meatball", "http://www.usemod.com/cgi-bin/mb.pl?");
    interWiki.put("SnipSnap", "http://snipsnap.org/space/");

    try {
      BufferedReader br = new BufferedReader(
          new InputStreamReader(
              new FileInputStream("conf/intermap.txt")));
      addInterMap(br);
    } catch (IOException e) {
      log.warn("Unable to read conf/intermap.txt");
    }
  }

  public void addInterMap(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      int index = line.indexOf(" ");
      interWiki.put(line.substring(0, index), Encoder.escape(line.substring(index + 1)));
    }
    ;
  }

  public Writer appendTo(Writer writer) throws IOException {
    Iterator iterator = interWiki.entrySet().iterator();
    writer.write("{table}\n");
    writer.write("Wiki|Url\n");
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

  public boolean contains(String external) {
    return interWiki.containsKey(external);
  }

  public String getWikiUrl(String wiki, String name) {
    return ((String) interWiki.get(wiki)) + name;
  }

  public Writer expand(Writer writer, String wiki, String name, String view, String anchor) throws IOException  {
    writer.write("<a href=\"");
    writer.write((String) interWiki.get(wiki));
    writer.write(name);
    if (!"".equals(anchor)) {
       writer.write("#");
       writer.write(anchor);
    }
    writer.write("\">");
    writer.write(view);
    writer.write("</a>");
    return writer;
  }

  public Writer expand(Writer writer, String wiki, String name, String view) throws IOException {
     return expand(writer, wiki, name, view, "");
  }
}
