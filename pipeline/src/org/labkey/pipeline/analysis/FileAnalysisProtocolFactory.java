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

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

/**
 * <code>FileAnalysisProtocolFactory</code>
 */
public class FileAnalysisProtocolFactory extends AbstractFileAnalysisProtocolFactory<FileAnalysisProtocol>
{
    private FileAnalysisTaskPipeline _pipeline;

    public FileAnalysisProtocolFactory(FileAnalysisTaskPipeline pipeline)
    {
        _pipeline = pipeline;
    }

    public FileAnalysisTaskPipeline getPipeline()
    {
        return _pipeline;
    }

    public String getName()
    {
        String name = _pipeline.getProtocolFactoryName();
        if (name != null)
            return name;
        return _pipeline.getId().getName();
    }

    public String getDefaultParametersXML(PipeRoot root) throws FileNotFoundException, IOException
    {
        String xml = super.getDefaultParametersXML(root);
        if (xml != null)
            return xml;
        
        // TODO: get defaults from the task factories
        return PipelineJobService.get().createParamParser().getXMLFromMap(new HashMap<>());
    }

    public FileAnalysisProtocol createProtocolInstance(String name, String description, String xml)
    {
        FileAnalysisProtocol protocol = new FileAnalysisProtocol(name, description, xml);
        protocol.setFactory(this);
        return protocol;
    }
}
