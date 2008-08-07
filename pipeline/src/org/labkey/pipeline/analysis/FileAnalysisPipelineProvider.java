/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.labkey.api.exp.pipeline.XarTemplateSubstitutionId;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProvider;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.FileFilter;
import java.util.List;
import java.util.ListIterator;

/**
 * <code>FileAnalysisPipelineProvider</code>
 */
public class FileAnalysisPipelineProvider extends AbstractFileAnalysisProvider<FileAnalysisProtocolFactory,
        FileAnalysisTaskPipeline>
{
    public static String name = "File Analysis";

    public FileAnalysisPipelineProvider()
    {
        super(name);
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

    public void updateFileProperties(ViewContext context, PipeRoot pr, List<FileEntry> entries)
    {
        Container c = context.getContainer();

        PipelineJobService service = PipelineJobService.get();
        FileAnalysisTaskPipeline[] pipelines = service.getTaskPipelines(FileAnalysisTaskPipeline.class);
        for (final FileAnalysisTaskPipeline tp : pipelines)
        {            
            for (ListIterator<FileEntry> it = entries.listIterator(); it.hasNext();)
            {
                FileEntry entry = it.next();
                if (!entry.isDirectory())
                    continue;

                String path = entry.cloneHref().getParameter(Params.path.toString());
                ActionURL url = AnalysisController.urlAnalyze(c, tp.getId(), path);
                addAction(url, tp.getDescription(),
                        entry, entry.listFiles(tp.getInitialFileTypeFilter()));

                FileFilter filter = getImportFilter(tp);
                if (filter != null)
                {
                    url = AnalysisController.urlImport(c, tp.getId(), path);
                    addAction(url, "Import", entry, entry.listFiles(filter));                    
                }
            }
        }
    }

    private FileFilter getImportFilter(FileAnalysisTaskPipeline tp)
    {
        TaskId[] progression = tp.getTaskProgression();
        if (progression == null)
            return null;
        
        for (TaskId id : progression)
        {
            if (id.getNamespaceClass().equals(XarTemplateSubstitutionId.class))
            {
                final XarTemplateSubstitutionId.Factory factory = (XarTemplateSubstitutionId.Factory)
                        PipelineJobService.get().getTaskFactory(id);
                final FileType[] types = factory.getInputTypes();
                if (types == null || types.length == 0)
                    return null;

                return new FileEntryFilter ()
                    {
                        public boolean accept(File f)
                        {
                            // If this is the input type to a xar generator
                            FileType typeInput = null;
                            for (FileType type : types)
                            {
                                if (type.isType(f))
                                {
                                    typeInput = type;
                                    break;
                                }
                            }
                            if (typeInput == null)
                                return false;

                            // But neither the output type, nor a plain XAR XML are present
                            File parent = f.getParentFile();
                            String baseName = typeInput.getBaseName(f);
                            return !fileExists(factory.getOutputType().newFile(parent, baseName)) &&
                                    !fileExists(XarGeneratorId.FT_XAR_XML.newFile(parent, baseName));
                        }
                    };
                
            }
        }

        return null;
    }
}
