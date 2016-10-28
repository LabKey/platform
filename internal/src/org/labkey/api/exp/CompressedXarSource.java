/*
 * Copyright (c) 2005-2016 LabKey Corporation
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

package org.labkey.api.exp;

import org.labkey.api.data.Container;
import org.labkey.api.util.FileUtil;
import org.labkey.api.pipeline.PipelineJob;

import java.io.*;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

/**
 * User: jeckels
 * Date: Dec 2, 2005
 */
public class CompressedXarSource extends AbstractFileXarSource
{
    private final int BUFFER_SIZE = 2048;

    private final File _xarFile;

    public CompressedXarSource(File xarFile, PipelineJob job)
    {
        super(job);
        _xarFile = xarFile;
    }

    /*
     * @param xarFile
     * @param job
     * @param targetContainer -- This is the container where the experiment and runs will get imported.
     *                           This may not be the same as the the Container returned by job.getContainer().
     */
    public CompressedXarSource(File xarFile, PipelineJob job, Container targetContainer)
    {
        super(job.getDescription(), targetContainer, job.getUser());
        _xarFile = xarFile;
    }

    public void init() throws IOException, ExperimentException
    {
        File outputDir = new File(_xarFile.getPath() + ".exploded");
        FileUtil.deleteDir(outputDir);
        if (outputDir.exists())
        {
            throw new ExperimentException("Failed to clean up old directory " + outputDir);
        }
        outputDir.mkdirs();
        if (!outputDir.isDirectory())
        {
            throw new ExperimentException("Failed to create directory " + outputDir);
        }

        try (FileInputStream fIn = new FileInputStream(_xarFile))
        {
            ZipInputStream zIn = new ZipInputStream(new BufferedInputStream(fIn));
            ZipEntry entry;
            while ((entry = zIn.getNextEntry()) != null)
            {
                byte data[] = new byte[BUFFER_SIZE];
                File destFile = new File(outputDir, entry.getName());
                if (entry.isDirectory())
                {
                    destFile.mkdirs();
                    if (!destFile.isDirectory())
                    {
                        throw new ExperimentException("Failed to create directory " + destFile);
                    }
                }
                else
                {
                    int i;
                    File destDir = destFile.getParentFile();
                    destDir.mkdirs();
                    if (!destDir.isDirectory())
                    {
                        throw new ExperimentException("Failed to create directory " + destDir);
                    }

                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(destFile), BUFFER_SIZE))
                    {
                        while ((i = zIn.read(data, 0, BUFFER_SIZE)) != -1)
                        {
                            out.write(data, 0, i);
                        }
                        if (destFile.getName().toLowerCase().endsWith(".xar.xml"))
                        {
                            if (_xmlFile != null)
                            {
                                throw new XarFormatException("XAR file " + _xarFile + " contains more than one .xar.xml file");
                            }
                            _xmlFile = destFile;
                        }
                    }
                }
            }

            if (_xmlFile == null)
            {
                throw new XarFormatException("XAR file " + _xarFile + " does not contain any .xar.xml files");
            }
        }
    }

    public File getLogFile() throws IOException
    {
        return getLogFileFor(_xarFile);
    }

    public String toString()
    {
        return _xarFile.getPath();
    }
}
