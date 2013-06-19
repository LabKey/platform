/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * User: adam
 * Date: Apr 28, 2009
 * Time: 2:00:23 PM
 */
public class ZipUtil
{
    // Unzip a zipped input stream to the specified directory
    public static List<File> unzipToDirectory(InputStream is, File unzipDir) throws IOException
    {
        return unzipToDirectory(is, unzipDir, null);
    }


    // Unzip a zipped file archive to the specified directory
    public static List<File> unzipToDirectory(File zipFile, File unzipDir) throws IOException
    {
        return unzipToDirectory(zipFile, unzipDir, null);
    }


    // Unzip an archive to the specified directory; log each file if Logger is non-null
    public static List<File> unzipToDirectory(File zipFile, File unzipDir, @Nullable Logger log) throws IOException
    {
        InputStream is = new FileInputStream(zipFile);
        return unzipToDirectory(is, unzipDir, log);
    }


    // Unzip all entries to the specified directory; log each file if Logger is non-null
    public static List<File> unzipToDirectory(Enumeration<? extends ZipEntry> entries, File unzipDir, InputStream zis, @Nullable Logger log) throws IOException
    {
        List<File> files = new ArrayList<>();

        while (entries.hasMoreElements())
        {
            ZipEntry entry = entries.nextElement();

            if (entry.isDirectory())
            {
                File newDir = new File(unzipDir, entry.getName());
                newDir.mkdir();
                continue;
            }

            if (null != log)
                log.info("Expanding " + entry.getName());

            BufferedInputStream is = null;
            BufferedOutputStream os = null;

            try
            {
                is = new BufferedInputStream(zis); // TODO: Make work for file -- zip.getInputStream(entry)
                File destFile = new File(unzipDir, entry.getName());
                destFile.getParentFile().mkdirs();
                destFile.createNewFile();
                os = new BufferedOutputStream(new FileOutputStream(destFile));
                FileUtil.copyData(is, os);
                files.add(destFile);
            }
            finally
            {
                //if (is != null) is.close();
                if (os != null) os.close();
            }
        }

        return files;
    }


    // Unzip an input stream to the specified directory; log each file if Logger is non-null
    public static List<File> unzipToDirectory(InputStream is, File unzipDir, @Nullable Logger log) throws IOException
    {
        List<File> files = null;

        try
        {
            ZipInputStream zis = new ZipInputStream(is);
            files = new ArrayList<>();
            ZipEntry entry;

            while (null != (entry = zis.getNextEntry()))
            {
                if (entry.isDirectory())
                {
                    File newDir = new File(unzipDir, entry.getName());
                    newDir.mkdir();
                    continue;
                }

                if (null != log)
                    log.info("Expanding " + entry.getName());

                BufferedOutputStream os = null;

                try
                {
                    is = new BufferedInputStream(zis);
                    File destFile = new File(unzipDir, entry.getName());
                    destFile.getParentFile().mkdirs();
                    destFile.createNewFile();
                    os = new BufferedOutputStream(new FileOutputStream(destFile));
                    FileUtil.copyData(is, os);
                    files.add(destFile);
                    zis.closeEntry();
                }
                finally
                {
                    //if (is != null) is.close();
                    if (os != null) os.close();
                }
            }
        }
        finally
        {
            is.close();
        }

        return files;
    }

    public static void zipToStream(HttpServletResponse response, File file, boolean preZipped) throws IOException
    {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + (preZipped ? "" : ".zip") + "\"");

        if (preZipped)
        {
            PageFlowUtil.streamFile(response, file, true);
            return;
        }

        ZipOutputStream zos = new ZipOutputStream(response.getOutputStream());

        try
        {
            addResource(file, zos);
        }
        finally
        {
            IOUtils.closeQuietly(zos);
        }
    }

    private static void addResource(File file, ZipOutputStream out) throws IOException
    {
        if (file.listFiles() != null)
        {
            for (File f : file.listFiles())
            {
                addResource(f, out);
            }
        }
        else
        {
            ZipEntry entry = new ZipEntry(file.getName());
            out.putNextEntry(entry);
            InputStream in = null;

            try
            {
                in = new FileInputStream(file);
                FileUtil.copyData(in, out);
            }
            finally
            {
                IOUtils.closeQuietly(in);
            }
        }
    }

    private static class ZipStreamEnumeration implements Enumeration<ZipEntry>
    {
        private ZipInputStream _zis;
        private ZipEntry _nextEntry;

        private ZipStreamEnumeration(ZipInputStream zis)
        {
            _zis = zis;
        }

        public boolean hasMoreElements()
        {
            try
            {
                if (null != _nextEntry)
                    _zis.closeEntry();

                _nextEntry = _zis.getNextEntry();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            return null != _nextEntry;
        }

        public ZipEntry nextElement()
        {
            return _nextEntry;
        }
    }
}
