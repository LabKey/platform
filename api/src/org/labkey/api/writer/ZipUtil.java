/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.FileUtil;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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


    // Unzip an archive to the specified directory; log each file if Loggger is non-null
    public static List<File> unzipToDirectory(File zipFile, File unzipDir, @Nullable Logger log) throws IOException
    {
        InputStream is = new FileInputStream(zipFile);
        return unzipToDirectory(is, unzipDir, log);
    }


    // Unzip all entries to the specified directory; log each file if Loggger is non-null
    public static List<File> unzipToDirectory(Enumeration<? extends ZipEntry> entries, File unzipDir, InputStream zis, @Nullable Logger log) throws IOException
    {
        List<File> files = new ArrayList<File>();

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


    // Unzip an input stream the specified directory; log each file if Loggger is non-null
    public static List<File> unzipToDirectory(InputStream is, File unzipDir, @Nullable Logger log) throws IOException
    {
        List<File> files = null;

        try
        {
            ZipInputStream zis = new ZipInputStream(is);
            files = new ArrayList<File>();
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
