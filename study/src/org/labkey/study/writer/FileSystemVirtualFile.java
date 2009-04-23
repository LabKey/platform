package org.labkey.study.writer;

import org.labkey.api.util.FileUtil;
import org.labkey.api.util.VirtualFile;

import java.io.PrintWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:30:23 PM
 */
public class FileSystemVirtualFile implements VirtualFile
{
    private File _root;

    public FileSystemVirtualFile(File root)
    {
        if (!root.exists())
            root.mkdir();

        ensureWriteableDirectory(root);

        _root = root;
    }

    private void ensureWriteableDirectory(File dir)
    {
        if (!dir.isDirectory())
            throw new IllegalStateException(dir.getAbsolutePath() + " is not a directory.");

        if (!dir.canWrite())
            throw new IllegalStateException("Can't write to " + dir.getAbsolutePath());
    }

    public PrintWriter getPrintWriter(String fileName) throws FileNotFoundException, UnsupportedEncodingException
    {
        File file = new File(_root, makeLegalName(fileName));

        return new PrintWriter(file);
    }

    public void makeDir(String path) throws FileNotFoundException, UnsupportedEncodingException
    {
        String[] parts = path.split("/");

        File parent = _root;

        for (String part : parts)
        {
            parent = new File(parent, makeLegalName(part));
            parent.mkdir();
        }

        ensureWriteableDirectory(parent);
    }

    public VirtualFile getDir(String name)
    {
        return new FileSystemVirtualFile(new File(_root, makeLegalName(name)));
    }

    public String makeLegalName(String name)
    {
        return FileUtil.makeLegalName(name);
    }
}
