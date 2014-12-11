package org.labkey.api.pipeline;

import java.io.IOException;

/**
 * Created by davebradlee on 12/8/14.
 */
public class DirectoryNotDeletedException extends IOException
{
    public DirectoryNotDeletedException()
    {
        super();
    }

    public DirectoryNotDeletedException(String message)
    {
        super(message);
    }
}
