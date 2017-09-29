/*
 * Copyright (c) 2009-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.writer;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.module.SafeFlushResponseWrapper;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.PrintWriters.StandardPrintWriter;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: adam
 * Date: Apr 27, 2009
 * Time: 5:29:58 PM
 */
public class ZipFile extends AbstractVirtualFile
{
    private final ZipOutputStream _out;
    private final String _path;
    private final PrintWriter _pw;
    private final boolean _shouldCloseOutputStream;
    private File _root;

    public ZipFile(File root, String name) throws FileNotFoundException
    {
        this(getOutputStream(root, name), true);
        _root = root;
    }

    public ZipFile(HttpServletResponse response, String name) throws IOException
    {
        this(getOutputStream(response, name), true);
    }

    public ZipFile(OutputStream out, boolean shouldCloseOutputStream)
    {
        // UTF-8 character set is used by default, but let's be explicit. Note that this UTF-8 declaration only affects the
        // file names and comments; character encoding of file contents is controlled by the PrintWriter.
        this(new ZipOutputStream(out, StandardCharsets.UTF_8), null, "", shouldCloseOutputStream);
    }

    private ZipFile(ZipOutputStream out, PrintWriter pw, String path, boolean shouldCloseOutputStream)
    {
        _out = out;
        _pw = null != pw ? pw : new NonCloseableUTF8PrintWriter(out);
        _path = path;
        _shouldCloseOutputStream = shouldCloseOutputStream;
    }

    private static OutputStream getOutputStream(File root, String name) throws FileNotFoundException
    {
        // Make sure directory exists, is writeable
        FileSystemFile.ensureWriteableDirectory(root);
        File zipFile = new File(root, _makeLegalName(name));
        FileOutputStream fos = new FileOutputStream(zipFile);

        return new BufferedOutputStream(fos);
    }

    private static OutputStream getOutputStream(HttpServletResponse response, String name) throws IOException
    {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + _makeLegalName(name) + "\";");

        return response.getOutputStream();
    }

    public String getLocation()
    {
        return _root != null ? _root.getAbsolutePath():"ZipFile stream.";
    }

    public PrintWriter getPrintWriter(String path) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(path));
        _out.putNextEntry(entry);

        return _pw;
    }

    public OutputStream getOutputStream(String path) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(path));
        _out.putNextEntry(entry);

        return new NonCloseableZipOutputStream(_out);
    }

    public void saveXmlBean(String filename, XmlObject doc) throws IOException
    {
        try
        {
            XmlBeansUtil.validateXmlDocument(doc, filename);
        }
        catch (XmlValidationException e)
        {
            throw new RuntimeException(e);
        }

        saveXmlBean(filename, doc, XmlBeansUtil.getDefaultSaveOptions());
    }

    // Expose this if/when some caller needs to customize the options
    private void saveXmlBean(String filename, XmlObject doc, XmlOptions options) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(filename));
        _out.putNextEntry(entry);
        doc.save(_out, options);
        _out.closeEntry();
    }

    public VirtualFile getDir(String path)
    {
        return new ZipFile(_out, _pw, _path + makeLegalName(path) + "/", false);
    }

    public VirtualFile createZipArchive(String name) throws IOException
    {
        ZipEntry entry = new ZipEntry(_path + makeLegalName(name));
        _out.putNextEntry(entry);

        return new ZipFile(_out, false);
    }

    public String makeLegalName(String name)
    {
        return _makeLegalName(name);
    }

    private static String _makeLegalName(String name)
    {
        return FileSystemFile.makeLegal(name);
    }

    public void close() throws IOException
    {
        _out.finish();            

        if (_shouldCloseOutputStream)
        {
            _out.close();
        }
    }

    private static class NonCloseableUTF8PrintWriter extends StandardPrintWriter
    {
        private final ZipOutputStream _out;

        private NonCloseableUTF8PrintWriter(ZipOutputStream out)
        {
            super(out);
            _out = out;
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

    private static class NonCloseableZipOutputStream extends SafeFlushResponseWrapper.OutputStreamWrapper
    {
        private final ZipOutputStream _out;

        private NonCloseableZipOutputStream(ZipOutputStream out)
        {
           super(out);
            _out = out;
        }

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

    @Override
    public XmlObject getXmlBean(String filename) throws IOException
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public InputStream getInputStream(String filename) throws IOException
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public String getRelativePath(String filename)
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public List<String> list()
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public List<String> listDirs()
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }

    @Override
    public boolean delete(String filename)
    {
        throw new UnsupportedOperationException("The ZipFile class only supports write operations");
    }
}
