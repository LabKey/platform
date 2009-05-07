package org.labkey.api.util;

import java.io.IOException;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:24:23 AM
 */
public interface Archive extends VirtualFile
{
    public void close() throws IOException;
}
