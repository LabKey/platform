package org.labkey.api.resource;

import org.labkey.api.util.Path;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Deprecated // TODO: Remove once newer version of trialServices gets published
public abstract class MergedDirectoryResource extends AbstractResourceCollection
{
    protected MergedDirectoryResource(Path path, Resolver resolver)
    {
        super(path, resolver);
    }

    @Deprecated
    public List<File> getContents()
    {
        File dir = getDir();
        return dir != null ? Collections.singletonList(dir) : Collections.emptyList();
    }

    abstract File getDir();
}
