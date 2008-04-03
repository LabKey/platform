/*
 * %W% %E%
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.sql;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import javax.xml.transform.Result;
import javax.xml.transform.Source;

public interface SQLXML
{
  void free() throws SQLException;

  InputStream getBinaryStream() throws SQLException;

  OutputStream setBinaryStream() throws SQLException;

  Reader getCharacterStream() throws SQLException;

  Writer setCharacterStream() throws SQLException;

  String getString() throws SQLException;

  void setString(String value) throws SQLException;

  <T extends Source> T getSource(Class<T> sourceClass) throws SQLException;

  <T extends Result> T setResult(Class<T> resultClass) throws SQLException;

}
