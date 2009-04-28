package org.labkey.study.writer;

import org.labkey.api.util.VirtualFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: adam
 * Date: Apr 27, 2009
 * Time: 5:29:58 PM
 */
public class ZipFile implements VirtualFile
{
    private final ZipOutputStream _out;
    private final String _path;
    private final PrintWriter _pw;

    public ZipFile(File root, String name) throws FileNotFoundException
    {
        // Make sure directory exists, is writeable
        FileSystemFile.ensureWriteableDirectory(root);
        File zipFile = new File(root, makeLegalName(name + ".zip"));
        FileOutputStream fos = new FileOutputStream(zipFile);
        _out = new ZipOutputStream(new BufferedOutputStream(fos));
        _pw = new NonCloseablePrintWriter(_out);
        _path = "";
    }

    public ZipFile(HttpServletResponse response, String name) throws IOException
    {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + makeLegalName(name + ".zip") + "\";");
        _out = new ZipOutputStream(response.getOutputStream());
        _pw = new NonCloseablePrintWriter(_out);
        _path = "";
    }

    private ZipFile(ZipOutputStream out, PrintWriter pw, String path)
    {
        _out = out;
        _pw = pw;
        _path = path + "/";
    }

    public PrintWriter getPrintWriter(String path) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(path));
        _out.putNextEntry(entry);

        return _pw;
    }

    public void makeDir(String path) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(path));
        _out.putNextEntry(entry);
    }

    public VirtualFile getDir(String path)
    {
        return new ZipFile(_out, _pw, _path + path);
    }

    public String makeLegalName(String name)
    {
        return FileSystemFile.makeLegal(name);
    }

    public void close() throws IOException
    {
        _out.flush();
        _out.close();
    }

    private class NonCloseablePrintWriter extends PrintWriter
    {
        private NonCloseablePrintWriter(OutputStream out)
        {
            super(out);
        }

        @Override
        public void close()
        {
            try
            {
                flush();
                _out.closeEntry();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
