package org.labkey.study.writer;

import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;

import java.io.PrintWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:30:23 PM
 */
public class FileExportContext implements ExportContext
{
    private File _root;
    private User _user;

    public FileExportContext(File root, User user)
    {
        _user = user;

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
        File file = new File(_root, fileName);

        return new PrintWriter(file);
    }

    public void ensurePath(String path) throws FileNotFoundException, UnsupportedEncodingException
    {
        File dir = new File(_root, path);

        dir.mkdirs();

        ensureWriteableDirectory(dir);
    }

    public String makeLegalName(String name)
    {
        return FileUtil.makeLegalName(name);
    }

    public User getUser()
    {
        return _user;
    }
}
