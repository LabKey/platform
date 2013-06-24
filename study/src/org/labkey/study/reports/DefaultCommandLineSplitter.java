package org.labkey.study.reports;

import java.io.IOException;

/**
 * User: adam
 * Date: 6/23/13
 * Time: 3:19 PM
 */
public class DefaultCommandLineSplitter implements CommandLineSplitter
{
    @Override
    public String[] getCommandStrings(String commandLine) throws IOException
    {
        return commandLine.split(" ");
    }
}
