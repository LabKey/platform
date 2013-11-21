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

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.*;
import org.labkey.api.view.HttpView;
import org.labkey.api.data.Container;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.api.TaskPipelineImpl;
import org.labkey.pipeline.xml.LocalOrRefTaskType;
import org.labkey.pipeline.xml.PipelineDocument;
import org.labkey.pipeline.xml.TaskPipelineType;
import org.labkey.pipeline.xml.TasksType;

import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisTaskPipelineImp</code>
 */
public class FileAnalysisTaskPipelineImpl extends TaskPipelineImpl<FileAnalysisTaskPipelineSettings> implements FileAnalysisTaskPipeline, Cloneable
{
    /** The text that will appear in the button to start this pipeline. */
    private String _description = "Analyze Data";
    private String _protocolFactoryName;
    private String _analyzeURL;
    private boolean _initialFileTypesFromTask;
    private List<FileType> _initialFileTypes;
    private Map<FileType, FileType[]> _typeHierarchy;
    /** If set, the default location for the action in the UI */
    private PipelineActionConfig.displayState _defaultDisplayState;

    public FileAnalysisTaskPipelineImpl()
    {
        super(new TaskId(FileAnalysisTaskPipeline.class));
    }

    public FileAnalysisTaskPipelineImpl(TaskId taskId)
    {
        super(taskId);
    }

    public TaskPipeline cloneAndConfigure(FileAnalysisTaskPipelineSettings settings, TaskId[] taskProgression) throws CloneNotSupportedException
    {
        FileAnalysisTaskPipelineImpl pipeline = (FileAnalysisTaskPipelineImpl)
                super.cloneAndConfigure(settings, taskProgression);

        return pipeline.configure(settings);
    }

    private TaskPipeline<FileAnalysisTaskPipelineSettings> configure(FileAnalysisTaskPipelineSettings settings)
    {
        if (settings.getDescription() != null)
            _description = settings.getDescription();

        if (settings.getProtocolFactoryName() != null)
            _protocolFactoryName = settings.getProtocolFactoryName();

        if (settings.getAnalyzeURL() != null)
            _analyzeURL = settings.getAnalyzeURL();

        // Convert any input filter extensions to array of file types.
        List<FileType> inputFilterExts = settings.getInitialInputExts();
        if (inputFilterExts != null)
        {
            _initialFileTypesFromTask = false;
            _initialFileTypes = inputFilterExts;
        }
        else if (_initialFileTypesFromTask || getInitialFileTypes() == null)
        {
            _initialFileTypesFromTask = true;
            TaskId tid = getTaskProgression()[0];
            TaskFactory factory = PipelineJobService.get().getTaskFactory(tid);
            _initialFileTypes = factory.getInputTypes();
        }

        // Misconfiguration: the user will never be able to start this pipeline
        if (_initialFileTypes == null || _initialFileTypes.isEmpty())
                throw new IllegalArgumentException("File analysis pipelines require at least one initial file type.");

        // Convert any input extension hierarchy into file types.
        Map<FileType, List<FileType>> extHierarchy = settings.getFileExtHierarchy();
        if (extHierarchy != null || _typeHierarchy == null)
            _typeHierarchy = new HashMap<>();

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

        if (settings.getDefaultDisplayState() != null)
        {
            _defaultDisplayState = settings.getDefaultDisplayState();
        }

        return this;
    }

    public PipelineActionConfig.displayState getDefaultDisplayState()
    {
        return _defaultDisplayState;
    }

    public String getDescription()
    {
        return _description;
    }

    public String getProtocolFactoryName()
    {
        return _protocolFactoryName;
    }

    @NotNull
    public List<FileType> getInitialFileTypes()
    {
        return _initialFileTypes;
    }

    @NotNull
    public FileFilter getInitialFileTypeFilter()
    {
        return new PipelineProvider.FileTypesEntryFilter(_initialFileTypes);
    }

    @NotNull
    public URLHelper getAnalyzeURL(Container c, String path)
    {
        if (_analyzeURL != null)
        {
            try
            {
                ViewContext context = HttpView.currentContext();
                StringExpression expressionCopy = StringExpressionFactory.createURL(_analyzeURL);
                if (expressionCopy instanceof HasViewContext)
                    ((HasViewContext)expressionCopy).setViewContext(context);
                URLHelper result = new URLHelper(expressionCopy.eval(context.getExtendedProperties()));
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

    @NotNull
    public Map<FileType, FileType[]> getTypeHierarchy()
    {
        return _typeHierarchy;
    }

    @Override
    public String toString()
    {
        return getDescription();
    }

    /**
     * Creates TaskPipeline from a file-based module <code>&lt;name>.pipeline.xml</code> config file.
     *
     * @param pipelineTaskId The taskid of the TaskPipeline
     * @param pipelineConfig The task pipeline definition.
     */
    public static FileAnalysisTaskPipeline create(TaskId pipelineTaskId, Resource pipelineConfig)
    {
        if (pipelineTaskId.getName() == null)
            throw new IllegalArgumentException("Task pipeline must by named");

        if (pipelineTaskId.getType() != TaskId.Type.pipeline)
            throw new IllegalArgumentException("Task pipeline must by of type 'pipeline'");

        if (pipelineTaskId.getModuleName() == null)
            throw new IllegalArgumentException("Task pipeline must be defined by a module");

        Module module = ModuleLoader.getInstance().getModule(pipelineTaskId.getModuleName());

        PipelineDocument doc;
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            doc = PipelineDocument.Factory.parse(pipelineConfig.getInputStream(), options);
            XmlBeansUtil.validateXmlDocument(doc, "Task pipeline config '" + pipelineConfig.getPath() + "'");
        }
        catch (XmlException |XmlValidationException |IOException e)
        {
            throw new IllegalArgumentException(e);
        }

        FileAnalysisTaskPipelineImpl pipeline = new FileAnalysisTaskPipelineImpl(pipelineTaskId);
        pipeline.setDeclaringModule(module);

        TaskPipelineType xpipeline = doc.getPipeline();
        if (xpipeline == null)
            throw new IllegalArgumentException("<pipeline> element required");

        if (!pipelineTaskId.getName().equals(xpipeline.getName()))
            throw new IllegalArgumentException(String.format("Task pipeline must have the name '%s'", pipelineTaskId.getName()));

        if (xpipeline.isSetDescription())
            pipeline._description = xpipeline.getDescription();

        if (xpipeline.isSetAnalyzeURL())
            pipeline._analyzeURL = xpipeline.getAnalyzeURL();

        // Resolve all the steps in the pipeline
        TaskFactory initialTaskFactory = null;
        List<TaskId> progression = new ArrayList<>();
        TasksType xtasks = xpipeline.getTasks();
        for (LocalOrRefTaskType task : xtasks.getTaskArray())
        {
            // UNDONE: provide task parameters

            if (task.isSetRef())
            {
                try
                {
                    TaskId taskId = TaskId.valueOf(task.getRef());
                    TaskFactory factory = PipelineJobService.get().getTaskFactory(taskId);
                    if (factory == null)
                        throw new IllegalArgumentException("Task factory ref not found: " + task.getRef());

                    if (initialTaskFactory == null)
                        initialTaskFactory = factory;

                    progression.add(taskId);
                }
                catch (ClassNotFoundException cnfe)
                {
                    throw new IllegalArgumentException("Task factory class not found: " + task.getRef());
                }
            }
            else
            {
                // UNDONE: local task definition
            }
        }

        if (initialTaskFactory == null)
            throw new IllegalArgumentException("Expected at least one task factory in the task pipeline");

        pipeline.setTaskProgression(progression.toArray(new TaskId[progression.size()]));

        // Initial file types
        pipeline._initialFileTypesFromTask = true;
        pipeline._initialFileTypes = initialTaskFactory.getInputTypes();

        // Misconfiguration: the user will never be able to start this pipeline
        if (pipeline._initialFileTypes == null || pipeline._initialFileTypes.isEmpty())
            throw new IllegalArgumentException("File analysis pipelines require at least one initial file type.");

        // CONSIDER: Attempt to map outputs from previous task to inputs of the next task

        // UNDONE: I don't understand the typeHierarchy
        // Add the initial types to the hierarchy
        pipeline._typeHierarchy = new HashMap<>();
        for (FileType ft : pipeline._initialFileTypes)
            pipeline._typeHierarchy.put(ft, new FileType[0]);

//        // UNDONE: Default display state
//        if (xpipeline.isSetDefaultDisplay())
//            pipeline._defaultDisplayState = PipelineActionConfig.displayState.valueOf(xpipeline.getDefaultDisplayState());

        return pipeline;
    }
}
