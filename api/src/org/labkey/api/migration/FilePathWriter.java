package org.labkey.api.migration;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;

import java.io.Closeable;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;

public class FilePathWriter implements Closeable
{
    private final PrintWriter _out;
    private final Path _rootPath;

    public FilePathWriter(PrintWriter out)
    {
        _out = out;
        _rootPath = FileContentService.get().getFileRoot(ContainerManager.getRoot()).toPath();
    }

    public void write(File file)
    {
        _out.println(_rootPath.relativize(file.toPath()).normalize());
    }

    public void println(String s)
    {
        _out.println(s);
    }

    public void println()
    {
        _out.println();
    }

    @Override
    public void close()
    {
        _out.close();
    }
}
