package org.labkey.api.pipeline.browse;

import org.labkey.api.pipeline.PipeRoot;

import java.io.File;

public class BrowseFile
{
    final private PipeRoot pipeRoot;
    final private File file;
    final private String relativePath;

    public BrowseFile(PipeRoot pipeRoot, File file)
    {
        this.file = file;
        this.pipeRoot = pipeRoot;
        this.relativePath = pipeRoot.relativePath(file);
    }

    /*public BrowseFile(PipeRoot pipeRoot, String path)
    {
        this.pipeRoot = pipeRoot;
        this.file = pipeRoot.resolvePath(path);
        this.relativePath = path;
    }*/

    public File getFile()
    {
        return file;
    }

    public PipeRoot getPipeRoot()
    {
        return pipeRoot;
    }

    public String getRelativePath()
    {
        return relativePath;
    }

    public boolean isDirectory()
    {
        return file.isDirectory();
    }

    public String getName()
    {
        return file.getName();
    }
}
