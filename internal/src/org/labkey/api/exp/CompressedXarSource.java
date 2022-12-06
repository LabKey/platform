/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: Dec 2, 2005
 */
public class CompressedXarSource extends AbstractFileXarSource
{
    private final Path _xarFile;

    public CompressedXarSource(Path xarFile, PipelineJob job)
    {
        super(job);
        _xarFile = xarFile;
    }

    /*
     * @param xarFile
     * @param job
     * @param targetContainer -- This is the container where the experiment and runs will get imported.
     *                           This may not be the same as the the Container returned by job.getContainer().
     * @param substitutions Additional context substitutions
     */
    public CompressedXarSource(Path xarFile, PipelineJob job, Container targetContainer, @Nullable Map<String, String> substitutions)
    {
        super(job.getDescription(), targetContainer, job.getUser(), job, substitutions);
        _xarFile = xarFile;
    }

    public CompressedXarSource(Path xarFile, PipelineJob job, Container targetContainer)
    {
        this(xarFile, job, targetContainer, null);
    }

    @Override
    public void init() throws ExperimentException, IOException
    {
        Path outputDir = _xarFile.resolve(_xarFile + ".exploded");
        FileUtil.deleteDir(outputDir);
        if (Files.exists(outputDir))
        {
            throw new ExperimentException("Failed to clean up old directory " + outputDir);
        }
        Files.createDirectories(outputDir);
        if (!Files.isDirectory(outputDir))
        {
            throw new ExperimentException("Failed to create directory " + outputDir);
        }

        List<Path> xarContents;
        try
        {
            xarContents = ZipUtil.unzipToDirectory(_xarFile, outputDir);
        }
        catch (IOException e)
        {
            throw new ExperimentException("Failed to extract XAR file: " + _xarFile, e);
        }

        List<Path> xarFiles = xarContents.stream().filter(f -> f.getFileName().toString().toLowerCase().endsWith(".xar.xml")).collect(Collectors.toList());

        if (xarFiles.isEmpty())
        {
            throw new XarFormatException("XAR file " + _xarFile + " does not contain any .xar.xml files");
        }
        else if (xarFiles.size() > 1)
        {
            throw new XarFormatException("XAR file " + _xarFile + " contains more than one .xar.xml file");
        }
        else
        {
            _xmlFile = xarFiles.get(0);
        }
    }

    @Override
    public Path getLogFilePath()
    {
        try
        {
            return getLogFileFor(_xarFile);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to access log file", e);
        }
    }

    public String toString()
    {
        return _xarFile.toString();
    }
}
