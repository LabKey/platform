package org.labkey.api.resource;

import org.labkey.api.util.Path;

import java.io.File;

@Deprecated // TODO: Remove once newer version of trialServices gets published
public class MergedDirectoryResource extends DirectoryResource
{
    public MergedDirectoryResource(Resolver resolver, Path path, File dir)
    {
        super(resolver, path, dir);
    }
}
