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

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * User: jeckels
 * Date: Oct 14, 2005
 */
public class FileXarSource extends AbstractFileXarSource
{
    @Deprecated
    public FileXarSource(File file, PipelineJob job)
    {
        super(job);
        _xmlFile = file.toPath();
    }

    public FileXarSource(Path file, PipelineJob job) throws IOException
    {
        super(job);
        _xmlFile = file.toRealPath();
    }

    @Deprecated
    public File getLogFile()
    {
        return getLogFilePath().toFile();
    }


    @Override
    public Path getLogFilePath()
    {
        try
        {
            return getLogFileFor(_xmlFile);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to access log file", e);
        }
    }

    public String toString()
    {
        return _xmlFile.toString();
    }
}
