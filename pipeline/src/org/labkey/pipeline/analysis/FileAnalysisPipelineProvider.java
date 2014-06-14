/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProvider;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ViewContext;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.InsertPermission;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
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
        Collection<FileAnalysisTaskPipeline> pipelines =
                service.getTaskPipelines(null, FileAnalysisTaskPipeline.class);
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
            return;

        // Unless showing all actions or including actions from disabled modules,
        // only include piplines from active modules in the current container.
        Container c = context.getContainer();
        if (includeAll || isShowActionsIfModuleInactive())
            c = null;

        Collection<FileAnalysisTaskPipeline> pipelines = PipelineJobService.get().getTaskPipelines(c, FileAnalysisTaskPipeline.class);
        for (final FileAnalysisTaskPipeline tp : pipelines)
        {            
            String path = directory.cloneHref().getParameter(Params.path.toString());
            String actionId = createActionId(this.getClass(), tp.getDescription()); // XXX: use task id instead so it's unique?
            addAction(actionId, tp.getAnalyzeURL(c, path), tp.getDescription(),
                    directory, directory.listFiles(tp.getInitialFileTypeFilter()), true, false, includeAll);
        }
    }

    @Override
    public List<PipelineActionConfig> getDefaultActionConfig(Container container)
    {
        List<PipelineActionConfig> result = new ArrayList<>();
        Collection<FileAnalysisTaskPipeline> pipelines = PipelineJobService.get().getTaskPipelines(container, FileAnalysisTaskPipeline.class);
        for (final FileAnalysisTaskPipeline tp : pipelines)
        {
            if (tp.getDefaultDisplayState() != null)
            {
                String actionId = createActionId(this.getClass(), tp.getDescription());
                result.add(new PipelineActionConfig(actionId, tp.getDefaultDisplayState(), tp.getDescription(), true));
            }
        }
        return result;
    }

    @Override
    public void preDeleteStatusFile(PipelineStatusFile sf)
    {
        // Delete the protocol analysis directory and it's contents if it is no longer used
        File statusFile = new File(sf.getFilePath());
        File analysisDir = statusFile.getParentFile();
        if (NetworkDrive.exists(analysisDir))
        {
            boolean unused = true;

            // Check if any runs use the analysis directory as the filePathRoot
            List<? extends ExpRun> runs = ExperimentService.get().getExpRunsForFilePathRoot(analysisDir);
            if (!runs.isEmpty())
            {
                unused = false;
            }

            if (unused)
            {
                // Check if any runs have ExpData inputs/outputs that reference files in this directory
                File[] files = analysisDir.listFiles();
                if (files != null)
                {
                    for (File file : files)
                    {
                        ExpRun run = ExperimentService.get().getCreatingRun(file, null);
                        if (run != null)
                        {
                            unused = false;
                            break;
                        }
                    }
                }
            }

            // If no usages were found, delete the entire analysis directory.
            // CONSIDER: Perform file delete in post-commit transaction
            if (unused)
            {
                if (FileUtil.moveToDeleted(analysisDir))
                    Logger.getLogger(FileAnalysisPipelineProvider.class).info(String.format("Job '%s' analysis directory no longer referenced by any runs and was moved to .deleted: %s", sf.getInfo(), analysisDir));
                else
                    Logger.getLogger(FileAnalysisPipelineProvider.class).warn(String.format("Failed to move job '%s' analysis directory to .deleted: %s", sf.getDescription(), analysisDir));
            }
        }
    }
}
