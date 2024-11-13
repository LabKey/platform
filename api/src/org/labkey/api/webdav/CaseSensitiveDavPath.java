package org.labkey.api.webdav;

import org.labkey.api.util.Path;

public class CaseSensitiveDavPath extends DavPath
{
    public CaseSensitiveDavPath(Path path)
    {
        super(path, true);
    }
}
