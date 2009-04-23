package org.labkey.api.util;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:28:31 PM
 */
public interface VirtualFile
{
    public PrintWriter getPrintWriter(String path) throws FileNotFoundException, UnsupportedEncodingException;
    public void makeDir(String path) throws FileNotFoundException, UnsupportedEncodingException;
    public VirtualFile getDir(String path);
    public String makeLegalName(String name);
}
