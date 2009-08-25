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

/**
 * User: adam
 * Date: Apr 28, 2009
 * Time: 2:00:23 PM
 */
public class ZipUtil
{
    // Unzip an archive to the specified directory
    public static List<File> unzipToDirectory(File zipFile, File unzipDir) throws IOException
    {
        return unzipToDirectory(zipFile, unzipDir, null);
    }


    // Unzip an archive to the specified directory; log each file if Loggger is non-null
    public static List<File> unzipToDirectory(File zipFile, File unzipDir, @Nullable Logger log) throws IOException
    {
        List<File> files = new ArrayList<File>();
        ZipFile zip = null;
        try
        {
            zip = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> entries = zip.entries();

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
                    is = new BufferedInputStream(zip.getInputStream(entry));
                    File destFile = new File(unzipDir, entry.getName());
                    destFile.getParentFile().mkdirs();
                    destFile.createNewFile();
                    os = new BufferedOutputStream(new FileOutputStream(destFile));
                    FileUtil.copyData(is, os);
                    files.add(destFile);
                }
                finally
                {
                    if (is != null) is.close();
                    if (os != null) os.close();
                }
            }
        }
        finally
        {
            if (zip != null) try { zip.close(); } catch (IOException e) {}
        }
        return files;
    }
}
