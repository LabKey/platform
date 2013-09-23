package org.labkey.api.files;

import org.labkey.api.util.ExceptionUtil;

import java.io.IOException;

/**
 * User: adam
 * Date: 9/19/13
 * Time: 10:06 AM
 */
public class FileSystemWatchers
{
    public static FileSystemWatcher get(String name)
    {
        try
        {
            return new FileSystemWatcherImpl(name);
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
            return new NoopFileSystemWatcher();
        }
    }
}
