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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * User: jeckels
 * Date: Oct 14, 2005
 */
public class FileXarSource extends AbstractFileXarSource
{
    public FileXarSource(Path file, PipelineJob job)
    {
        super(job);
        _xmlFile = file.normalize();
    }

    public FileXarSource(Path file, PipelineJob job, Container targetContainer, @Nullable Map<String, String> substitutions)
    {
        super(job.getDescription(), targetContainer, job.getUser(), job, substitutions);
        _xmlFile = file;
    }

    public FileXarSource(Path file, PipelineJob job, Container targetContainer)
    {
        this(file, job, targetContainer, null);
    }

    @Override
    public Path getLogFilePath() throws IOException
    {
        return getLogFileFor(_xmlFile);
    }

    public String toString()
    {
        return _xmlFile.toString();
    }
}
