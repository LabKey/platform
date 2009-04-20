package org.labkey.study.writer;

import org.labkey.api.security.User;

import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:28:31 PM
 */
public interface ExportContext
{
    public PrintWriter getPrintWriter(String path) throws FileNotFoundException, UnsupportedEncodingException;
    public void ensurePath(String path) throws FileNotFoundException, UnsupportedEncodingException;
    public String makeLegalName(String name);
    public User getUser();
}
