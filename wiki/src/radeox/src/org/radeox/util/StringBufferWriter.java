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

package org.radeox.util;

import java.io.IOException;
import java.io.Writer;

/**
 * The same as StringWriter, but takes an existing StringBuffer in its
 * constructor.
 *
 * @author Stephan J. Schmidt
 * @version $Id: StringBufferWriter.java,v 1.2 2003/02/06 13:41:42 leo Exp $
 */

public class StringBufferWriter extends Writer {

  private StringBuffer buffer;

  private boolean closed = false;

  public StringBufferWriter(StringBuffer buffer) {
    this.buffer = buffer;
    this.lock = buffer;
  }

  public StringBufferWriter() {
    this.buffer = new StringBuffer();
    this.lock = buffer;
  }

  public StringBufferWriter(int initialSize) {
    if (initialSize < 0) {
      throw new IllegalArgumentException("Negative buffer size");
    }
    buffer = new StringBuffer(initialSize);
    lock = buffer;
  }

   public void write(int c) {
    buffer.append((char) c);
  }

   public void write(char cbuf[], int off, int len) {
    if ((off < 0) || (off > cbuf.length) || (len < 0) ||
        ((off + len) > cbuf.length) || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    } else if (len == 0) {
      return;
    }
    buffer.append(cbuf, off, len);
  }

  public void write(String str) {
    buffer.append(str);
  }


  public void write(String str, int off, int len) {
    buffer.append(str.substring(off, off + len));
  }

  public String toString() {
    return buffer.toString();
  }

  public StringBuffer getBuffer() {
    return buffer;
  }

  public void flush() {
  }

  public void close() throws IOException {
    closed = true;
  }

}
