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

import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.util.*;
import org.labkey.api.view.HttpView;
import org.labkey.api.data.Container;
import org.labkey.pipeline.api.TaskPipelineImpl;

import java.io.File;
import java.io.FileFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisTaskPipelineImp</code>
 */
public class FileAnalysisTaskPipelineImpl extends TaskPipelineImpl<FileAnalysisTaskPipelineSettings> implements FileAnalysisTaskPipeline, Cloneable
{
    /**
     * The text that will appear in the button to start this pipeline.
     */
    private String _description = "Analyze Data";
    private String _protocolFactoryName;
    private StringExpression _analyzeURL;
    private boolean _initialFileTypesFromTask;
    private FileType[] _initialFileTypes;
    private Map<FileType, FileType[]> _typeHierarchy;

    public FileAnalysisTaskPipelineImpl()
    {
        super(new TaskId(FileAnalysisTaskPipeline.class));
    }

    public TaskPipeline cloneAndConfigure(FileAnalysisTaskPipelineSettings settings, TaskId[] taskProgression) throws CloneNotSupportedException
    {
        FileAnalysisTaskPipelineImpl pipeline = (FileAnalysisTaskPipelineImpl)
                super.cloneAndConfigure(settings, taskProgression);

        return pipeline.configure(settings);
    }

    private TaskPipeline configure(FileAnalysisTaskPipelineSettings settings)
    {
        if (settings.getDescription() != null)
            _description = settings.getDescription();

        if (settings.getProtocolFactoryName() != null)
            _protocolFactoryName = settings.getProtocolFactoryName();

        if (settings.getAnalyzeURL() != null)
            _analyzeURL = StringExpressionFactory.createURL(settings.getAnalyzeURL());

        // Convert any input filter extensions to array of file types.
        List<FileType> inputFilterExts = settings.getInitialInputExts();
        if (inputFilterExts != null)
        {
            _initialFileTypesFromTask = false;
            _initialFileTypes = inputFilterExts.toArray(new FileType[inputFilterExts.size()]);
        }
        else if (_initialFileTypesFromTask || getInitialFileTypes() == null)
        {
            _initialFileTypesFromTask = true;
            TaskId tid = getTaskProgression()[0];
            TaskFactory factory = PipelineJobService.get().getTaskFactory(tid);
            _initialFileTypes = factory.getInputTypes();
        }

        // Misconfiguration: the user will never be able to start this pipeline
        if (_initialFileTypes == null || _initialFileTypes.length == 0)
                throw new IllegalArgumentException("File analysis pipelines require at least one initial file type.");

        // Convert any input extension hierarchy into file types.
        Map<FileType, List<FileType>> extHierarchy = settings.getFileExtHierarchy();
        if (extHierarchy != null || _typeHierarchy == null)
            _typeHierarchy = new HashMap<FileType, FileType[]>();

        // Add the initial types to the hierarchy
        for (FileType ft : _initialFileTypes)
            _typeHierarchy.put(ft, new FileType[0]);

        if (extHierarchy != null)
        {
            for (Map.Entry<FileType, List<FileType>> entry  : extHierarchy.entrySet())
            {
                List<FileType> inputExtList = entry.getValue();
                FileType[] hierarchy = inputExtList.toArray(new FileType[inputExtList.size()]);
                _typeHierarchy.put(entry.getKey(), hierarchy);
            }
        }

        return this;
    }

    public String getDescription()
    {
        return _description;
    }

    public String getProtocolFactoryName()
    {
        return _protocolFactoryName;
    }

    public FileType[] getInitialFileTypes()
    {
        return _initialFileTypes;
    }

    public FileFilter getInitialFileTypeFilter()
    {
        return new PipelineProvider.FileTypesEntryFilter(_initialFileTypes);
    }

    public URLHelper getAnalyzeURL(Container c, String path)
    {
        if (_analyzeURL != null)
        {
            try
            {
                URLHelper result = new URLHelper(_analyzeURL.eval(HttpView.currentContext()));
                if (result.getParameter("path") == null)
                {
                    result.addParameter("path", path);
                }
                return result;
            }
            catch (URISyntaxException e)
            {
                throw new UnexpectedException(e);
            }
        }
        return AnalysisController.urlAnalyze(c, getId(), path);
    }

    public Map<FileType, FileType[]> getTypeHierarchy()
    {
        return _typeHierarchy;
    }
}
