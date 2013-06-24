package org.labkey.study.reports;

import java.io.IOException;

/**
 * User: adam
 * Date: 6/23/13
 * Time: 3:16 PM
 */
public interface CommandLineSplitter
{
    public String[] getCommandStrings(String commandLine) throws IOException;
}
