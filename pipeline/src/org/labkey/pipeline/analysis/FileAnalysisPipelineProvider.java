/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProvider;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.view.ViewContext;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.InsertPermission;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * <code>FileAnalysisPipelineProvider</code>
 */
public class FileAnalysisPipelineProvider extends AbstractFileAnalysisProvider<FileAnalysisProtocolFactory,
        FileAnalysisTaskPipeline>
{
    public static String name = "File Analysis";

    public FileAnalysisPipelineProvider(Module owningModule)
    {
        super(name, owningModule);
    }

    public FileAnalysisProtocolFactory getProtocolFactory(FileAnalysisTaskPipeline pipeline)
    {
        return new FileAnalysisProtocolFactory(pipeline);
    }

    public FileAnalysisProtocolFactory getProtocolFactory(File fileParams)
    {
        // Check all possible FileAnalysisProtocolFactory types.
        PipelineJobService service = PipelineJobService.get();
        FileAnalysisTaskPipeline[] pipelines =
                service.getTaskPipelines(FileAnalysisTaskPipeline.class);
        for (FileAnalysisTaskPipeline tp : pipelines)
        {
            FileAnalysisProtocolFactory factory = getProtocolFactory(tp);
            if (factory.isProtocolTypeFile(fileParams))
                return factory;
        }

        return null;
    }

    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
        {
            return;
        }

        Container c = context.getContainer();

        FileAnalysisTaskPipeline[] pipelines = PipelineJobService.get().getTaskPipelines(FileAnalysisTaskPipeline.class);
        for (final FileAnalysisTaskPipeline tp : pipelines)
        {            
            String path = directory.cloneHref().getParameter(Params.path.toString());
            String actionId = createActionId(this.getClass(), tp.getDescription());
            addAction(actionId, tp.getAnalyzeURL(c, path), tp.getDescription(),
                    directory, directory.listFiles(tp.getInitialFileTypeFilter()), true, false, includeAll);
        }
    }

    @Override
    public List<PipelineActionConfig> getDefaultActionConfig(Container container)
    {
        List<PipelineActionConfig> result = new ArrayList<>();
        FileAnalysisTaskPipeline[] pipelines = PipelineJobService.get().getTaskPipelines(FileAnalysisTaskPipeline.class);
        for (final FileAnalysisTaskPipeline tp : pipelines)
        {
            if (tp.getDefaultDisplayState() != null && container.getActiveModules().contains(tp.getDeclaringModule()))
            {
                String actionId = createActionId(this.getClass(), tp.getDescription());
                result.add(new PipelineActionConfig(actionId, tp.getDefaultDisplayState(), tp.getDescription(), true));
            }
        }
        return result;
    }
}
