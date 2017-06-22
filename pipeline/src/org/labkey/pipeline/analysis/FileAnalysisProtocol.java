/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.pipeline.analysis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisProtocol</code>
 */
public class FileAnalysisProtocol extends AbstractFileAnalysisProtocol<AbstractFileAnalysisJob>
{
    private FileAnalysisProtocolFactory _factory;

    public FileAnalysisProtocol(String name, String description, String xml)
    {
        super(name, description, xml);
    }

    @NotNull
    public List<FileType> getInputTypes()
    {
        return _factory.getPipeline().getInitialFileTypes();
    }

    public AbstractFileAnalysisProtocolFactory getFactory()
    {
        return _factory;
    }

    public void setFactory(FileAnalysisProtocolFactory factory)
    {
        _factory = factory;
    }

    public AbstractFileAnalysisJob createPipelineJob(ViewBackgroundInfo info, PipeRoot root, List<File> filesInput,
                                                     File fileParameters, @Nullable Map<String, String> variableMap
    ) throws IOException
    {
        TaskId id = _factory.getPipeline().getId();

        boolean splittable = _factory.getPipeline().isSplittable();
        boolean writeJobInfoFile = _factory.getPipeline().isWriteJobInfoFile();

        return new FileAnalysisJob(this, FileAnalysisPipelineProvider.name, info, root,
                id, getName(), fileParameters, filesInput, variableMap, splittable, writeJobInfoFile);
    }
}
