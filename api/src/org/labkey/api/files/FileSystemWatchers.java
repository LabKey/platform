package org.labkey.api.files;

import org.labkey.api.settings.AppProps;
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
        // TODO: For now, return a real FileSystemWatcher in dev mode only. In the future,
        if (AppProps.getInstance().isDevMode())
        {
            try
            {
                return new FileSystemWatcherImpl(name);
            }
            catch (IOException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        return new NoopFileSystemWatcher();
    }
}
