package org.labkey.api.webdav;

import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

public class DavPath extends Path
{
    public DavPath(Path path)
    {
        super(path);
    }

    @Override
    protected int compareName(String a, String b)
    {
        if (FileUtil.isCaseInsensitiveFileSystem())
            return a.compareToIgnoreCase(b);

        return a.compareTo(b);
    }
}
