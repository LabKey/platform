/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.CheckedInputStream;
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

    public static List<File> unzipToDirectory(File zipFile, File unzipDir, @Nullable Logger log) throws IOException
    {
        return unzipToDirectory(zipFile, unzipDir, log, false);
    }

    // Unzip an archive to the specified directory; log each file if Logger is non-null
    public static List<File> unzipToDirectory(File zipFile, File unzipDir, @Nullable Logger log, boolean includeFolder) throws IOException
    {
        InputStream is = new FileInputStream(zipFile);
        return unzipToDirectory(is, unzipDir, log, includeFolder);
    }

    public static List<File> unzipToDirectory(InputStream is, File unzipDir, @Nullable Logger log) throws IOException
    {
        return unzipToDirectory(is, unzipDir, log, false);
    }

    // Unzips an input stream to the specified directory; logs each file if Logger is non-null.
    public static List<File> unzipToDirectory(InputStream is, File unzipDir, @Nullable Logger log, boolean includeFolder) throws IOException
    {
        List<File> files = new ArrayList<>();

        // ZipInputStream.close() should close InputStream is. Use a CheckedInputStream to be sure.
        try (ZipInputStream zis = new ZipInputStream(new CheckedInputStream(is)))
        {
            ZipEntry entry;

            while (null != (entry = zis.getNextEntry()))
            {
                File destFile = new File(unzipDir, entry.getName());

                if (!destFile.getCanonicalPath().startsWith(unzipDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Zip entry is outside of the target dir: " + entry.getName());
                }

                if (entry.isDirectory())
                {
                    destFile.mkdirs();
                    if (!destFile.isDirectory())
                    {
                        throw new IOException("Failed to create directory: " + destFile.getName());
                    }
                    if (includeFolder)
                        files.add(destFile);
                    continue;
                }

                if (null != log)
                    log.info("Expanding " + entry.getName());

                destFile.getParentFile().mkdirs();
                if (destFile.exists())
                {
                    throw new IOException("File already exists: " + destFile.getName());
                }
                if (!destFile.createNewFile())
                {
                    throw new IOException("Failed to extract file: " + destFile.getName());
                }

                // We can't close() this, otherwise zis will get closed
                BufferedInputStream bis = new BufferedInputStream(zis);

                try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(destFile)))
                {
                    FileUtil.copyData(bis, os);
                }

                files.add(destFile);
                zis.closeEntry();
            }
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

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream()))
        {
            addResource(file, zos);
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

            try (InputStream in = new FileInputStream(file))
            {
                FileUtil.copyData(in, out);
            }
        }
    }
}
