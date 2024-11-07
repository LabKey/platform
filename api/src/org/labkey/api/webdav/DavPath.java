package org.labkey.api.webdav;

import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

public class DavPath extends Path
{
    private static final boolean isCaseSensitive = !FileUtil.isCaseInsensitiveFileSystem();

    public DavPath(Path path)
    {
        super(path, isCaseSensitive);
    }

    public DavPath(Path path, boolean caseSensitive)
    {
        super(path, caseSensitive);
    }
}
