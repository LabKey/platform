/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.data.Container;
import org.labkey.api.formSchema.CheckboxField;
import org.labkey.api.formSchema.Field;
import org.labkey.api.formSchema.FormSchema;
import org.labkey.api.formSchema.NumberField;
import org.labkey.api.formSchema.Option;
import org.labkey.api.formSchema.RadioField;
import org.labkey.api.formSchema.SelectField;
import org.labkey.api.formSchema.TextField;
import org.labkey.api.formSchema.TextareaField;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineRegistry;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.TaskPipelineImpl;
import org.labkey.pipeline.xml.PipelineDocument;
import org.labkey.pipeline.xml.TaskPipelineType;
import org.labkey.pipeline.xml.TaskRefType;
import org.labkey.pipeline.xml.TaskType;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
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
    private boolean _initialFileTypesRequired = true;
    private FilePathFilter _initialInputFileFilter;
    private Map<FileType, List<FileType>> _typeHierarchy;
    /** If set, the default location for the action in the UI */
    private PipelineActionConfig.displayState _defaultDisplayState;
    private boolean _allowForTriggerConfiguration = false;
    private boolean _splittable = true;
    private boolean _writeJobInfoFile = false;
    /** Text to show at time of Task Pipeline configuration */
    private String _helpText;
    /** Whether to allow the task to move files during file analysis. Default is true */
    private Boolean _moveAvailable = true;

    /** Below are variables used to generate a FormSchema so we can instruct the client how to render a form */
    // new HelpTopic("fileWatchCreate").getHelpTopicHref(), generates a link pointing to archived docs. Is there a better way?
    private static final String BASE_HREF = "https://www.labkey.org/Documentation/wiki-page.view?name=fileWatchCreate#";
    private static final String LOCATION_HELP_TEXT = "This can be an absolute path on the server's file system or a relative path under the container's pipeline root.";
    private static final String LOCATION_HREF = BASE_HREF + "location";
    private static final String FILE_PATTERN_HELP_TEXT = "A Java regular expression that captures filenames of interest and can extract and use information from the filename to set other properties.";
    private static final String FILE_PATTERN_HREF = BASE_HREF + "filepattern";
    private static final String QUIET_HELP_TEXT = "Number of seconds to wait after file activity before executing a job (minimum is 1).";
    private static final String QUIET_HREF = BASE_HREF + "quietperiod";
    private static final String MOVE_CONTAINER_HELP_TEXT = "Move the file to this container before analysis. This must be a relative or absolute container path.";
    private static final String MOVE_CONTAINER_HREF = BASE_HREF + "moveto";
    private static final String MOVE_DIRECTORY_HELP_TEXT = "Move the file to this directory underneath the destination container's pipeline root. Leaving this blank will default to the pipeline root directory.";
    private static final String MOVE_DIRECTORY_HREF = BASE_HREF + "subdirectory";
    private static final String COPY_HELP_TEXT = "Where the file should be copied to before analysis. This can be absolute or relative to the current project/folder.";
    private static final String COPY_HREF = BASE_HREF + "copyto";
    private static final List<Field<?>> _defaultFields = List.of(
            new TextField("location", "Location to Watch", "./", false, null, LOCATION_HELP_TEXT, LOCATION_HREF),
            new CheckboxField("recursive", "Include Child Folders", false, false),
            new TextField("filePattern", "File Pattern", "(^\\D*)\\.(?:tsv|txt|xls|xlsx)", false, null, FILE_PATTERN_HELP_TEXT, FILE_PATTERN_HREF),
            new NumberField("quiet", "Quiet Period (Seconds)", null, true, 1.0, QUIET_HELP_TEXT, QUIET_HREF),
            new TextField("moveContainer", "Move to Container", "/Other Project/Subfolder A", false, null, MOVE_CONTAINER_HELP_TEXT, MOVE_CONTAINER_HREF),
            new TextField("moveDirectory", "Move to Subdirectory", "My Watched Files/Move", false, null, MOVE_DIRECTORY_HELP_TEXT, MOVE_DIRECTORY_HREF),
            new TextField("copy", "Copy File To", null, false, null, COPY_HELP_TEXT, COPY_HREF)
    );
    private List<Field<?>> _customFields;

    public FileAnalysisTaskPipelineImpl()
    {
        super(new TaskId(FileAnalysisTaskPipeline.class));
    }

    public FileAnalysisTaskPipelineImpl(TaskId taskId)
    {
        super(taskId);
    }

    @Override
    public TaskPipeline<FileAnalysisTaskPipelineSettings> cloneAndConfigure(FileAnalysisTaskPipelineSettings settings, TaskId[] taskProgression) throws CloneNotSupportedException
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

        if (settings.isInitialInputExtsRequired())
        {
            // Convert any input filter extensions to array of file types.
            List<FileType> inputFilterExts = settings.getInitialInputExts();
            if (inputFilterExts != null)
            {
                _initialFileTypesFromTask = false;
                _initialFileTypes = inputFilterExts;
            }
            else if (_initialFileTypesFromTask)
            {
                TaskId tid = getTaskProgression()[0];
                TaskFactory<?> factory = PipelineJobService.get().getTaskFactory(tid);
                _initialFileTypes = factory.getInputTypes();
            }

            if (settings.getInitialInputFileFilter() != null)
                _initialInputFileFilter = settings.getInitialInputFileFilter();

            // Misconfiguration: the user will never be able to start this pipeline
            if ((_initialFileTypes == null || _initialFileTypes.isEmpty()) && _initialInputFileFilter == null)
                throw new IllegalArgumentException("File analysis pipelines require at least one initial file type.");

            // Convert any input extension hierarchy into file types.
            Map<FileType, List<FileType>> extHierarchy = settings.getFileExtHierarchy();
            if (extHierarchy != null || _typeHierarchy == null)
                _typeHierarchy = new HashMap<>();

            // Add the initial types to the hierarchy
            for (FileType ft : _initialFileTypes)
                _typeHierarchy.put(ft, Collections.emptyList());

            if (extHierarchy != null)
            {
                for (Map.Entry<FileType, List<FileType>> entry  : extHierarchy.entrySet())
                {
                    List<FileType> inputExtList = entry.getValue();
                    _typeHierarchy.put(entry.getKey(), Collections.unmodifiableList(inputExtList));
                }
            }
        } else {
            // protocol will accept every kind of file
            _initialFileTypesRequired = false;
            _initialFileTypes = Collections.emptyList();
        }

        if (settings.getDefaultDisplayState() != null)
            _defaultDisplayState = settings.getDefaultDisplayState();

        if (settings.isAllowForTriggerConfiguration())
            _allowForTriggerConfiguration = true;

        if (settings.getHelpText() != null)
            _helpText = settings.getHelpText();

        if (settings.isMoveAvailable() != null)
            _moveAvailable = settings.isMoveAvailable();

        if (settings.getCustomFields() != null)
            _customFields = settings.getCustomFields();

        return this;
    }

    @Override
    public PipelineActionConfig.displayState getDefaultDisplayState()
    {
        return _defaultDisplayState;
    }

    @Override
    public boolean isAllowForTriggerConfiguration()
    {
        return _allowForTriggerConfiguration;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public String getProtocolFactoryName()
    {
        return _protocolFactoryName;
    }

    @Override
    @NotNull
    public List<FileType> getInitialFileTypes()
    {
        return _initialFileTypes;
    }

    @Override
    @NotNull
    public FilePathFilter getInitialFileTypeFilter()
    {
        if (!isInitialFileTypesRequired()) {
            return new PipelineProvider.FileEntryFilter()
            {
                @Override
                public boolean accept(java.nio.file.Path pathname)
                {
                    return true;
                }

                @Override
                public boolean accept(File pathname)
                {
                    return true;
                }
            };
        }

        if (_initialInputFileFilter != null)
            return _initialInputFileFilter;

        return new PipelineProvider.FileTypesEntryFilter(_initialFileTypes);
    }

    @Override
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
                if (result.getParameter("taskId") == null)
                {
                    result.addParameter("taskId", getId().toString());
                }
                return result;
            }
            catch (URISyntaxException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }
        return AnalysisController.urlAnalyze(c, getId(), path);
    }

    @Override
    @NotNull
    public Map<FileType, List<FileType>> getTypeHierarchy()
    {
        return _typeHierarchy;
    }

    @Override
    public boolean isWriteJobInfoFile()
    {
        return _writeJobInfoFile;
    }

    @Override
    public boolean isSplittable()
    {
        return _splittable;
    }

    @Override
    public String toString()
    {
        return getDescription();
    }

    @Override
    public String getHelpText()
    {
        return _helpText;
    }

    @Override
    public Boolean isMoveAvailable()
    {
        return _moveAvailable;
    }

    @Override
    public Boolean isInitialFileTypesRequired()
    {
        return _initialFileTypesRequired;
    }

    public List<Field<?>> getCustomFields()
    {
        return _customFields;
    }

    public void setCustomFields(List<Field<?>> customFields)
    {
        _customFields = customFields;
    }

    /**
     * Creates TaskPipeline from a file-based module <code>&lt;name>.pipeline.xml</code> config file
     * and registers any local TaskFactory definitions and this TaskPipeline with the PipelineJobService.
     *
     * @param pipelineTaskId The taskid of the TaskPipeline
     * @param pipelineConfig The task pipeline definition.
     */
    public static FileAnalysisTaskPipeline create(Module module, Resource pipelineConfig, TaskId pipelineTaskId)
    {
        if (pipelineTaskId.getName() == null)
            throw new IllegalArgumentException("Task pipeline must by named");

        if (pipelineTaskId.getType() != TaskId.Type.pipeline)
            throw new IllegalArgumentException("Task pipeline must be of type 'pipeline'");

        if (pipelineTaskId.getModuleName() == null)
            throw new IllegalArgumentException("Task pipeline must be defined by a module");

        PipelineDocument doc;
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            doc = PipelineDocument.Factory.parse(pipelineConfig.getInputStream(), options);
            XmlBeansUtil.validateXmlDocument(doc, "Task pipeline config '" + pipelineConfig.getPath() + "'");
        }
        catch (XmlValidationException e)
        {
            PipelineJobServiceImpl.LOG.error(e);
            return null;
        }
        catch (XmlException |IOException e)
        {
            PipelineJobServiceImpl.LOG.error("Error loading task pipeline '" + pipelineConfig.getPath() + "':\n" + e.getMessage());
            return null;
        }

        FileAnalysisTaskPipelineImpl pipeline = new FileAnalysisTaskPipelineImpl(pipelineTaskId);
        pipeline.setDeclaringModule(module);
        pipeline._protocolFactoryName = pipelineTaskId.getName();

        TaskPipelineType xpipeline = doc.getPipeline();
        if (xpipeline == null)
            throw new IllegalArgumentException("<pipeline> element required");

        if (!pipelineTaskId.getName().equals(xpipeline.getName()))
            throw new IllegalArgumentException(String.format("Task pipeline must have the name '%s'", pipelineTaskId.getName()));

        if (xpipeline.isSetDescription())
            pipeline._description = xpipeline.getDescription();

        if (xpipeline.isSetHelp())
            pipeline._helpText = xpipeline.getHelp();

        if (xpipeline.isSetAnalyzeURL())
            pipeline._analyzeURL = xpipeline.getAnalyzeURL();

        // Resolve all the steps in the pipeline
        List<TaskId> progression = new ArrayList<>();
        XmlObject[] xtasks = xpipeline.getTasks().selectPath("./*");
        for (int taskIndex = 0; taskIndex < xtasks.length; taskIndex++)
        {
            XmlObject xobj = xtasks[taskIndex];
            if (xobj instanceof TaskRefType xtaskref)
            {
                try
                {
                    TaskId taskId = TaskId.valueOf(xtaskref.getRef());
                    TaskFactory<?> factory = PipelineJobService.get().getTaskFactory(taskId);
                    if (factory == null)
                        throw new IllegalArgumentException("Task factory ref not found: " + xtaskref.getRef());

                    // UNDONE: Use settings to configure a task reference
                    /*
                    if (xtaskref.isSetSettings())
                    {
                        // Create settings from xml
                        TaskFactorySettings settings = createSettings(pipelineTaskId, factory, xtaskref.getSettings());
                        if (settings.getId().equals(taskId))
                            throw new IllegalArgumentException("Task factory settings must not be identical to parent task: " + settings.getId());

                        // Register locally configured task
                        try
                        {
                            PipelineJobServiceImpl.get().addLocalTaskFactory(pipeline, settings);
                        }
                        catch (CloneNotSupportedException e)
                        {
                            throw new IllegalArgumentException("Failed to register task with settings: " + taskId, e);
                        }

                        taskId = settings.getId();
                    }
                    */

                    progression.add(taskId);
                }
                catch (ClassNotFoundException cnfe)
                {
                    throw new IllegalArgumentException("Task factory class not found: " + xtaskref.getRef());
                }

            }
            else if (xobj instanceof TaskType xtask)
            {
                // Create a new local task definition

                String name = xtask.schemaType().getName().getLocalPart() + "-" + taskIndex;
                if (xtask.isSetName())
                    name = xtask.getName();

                TaskId localTaskId = createLocalTaskId(pipelineTaskId, name);

                Path tasksDir = pipelineConfig.getPath().getParent();
                TaskFactory<?> factory = PipelineJobServiceImpl.get().createTaskFactory(localTaskId, xtask, tasksDir);
                if (factory == null)
                    throw new IllegalArgumentException("Task factory not found: " + localTaskId);

                PipelineJobServiceImpl.get().addLocalTaskFactory(pipelineTaskId, factory);

                progression.add(localTaskId);
            }
        }

        if (progression.isEmpty())
            throw new IllegalArgumentException("Expected at least one task factory in the task pipeline");

        TaskFactory<?> initialTaskFactory = PipelineJobService.get().getTaskFactory(progression.get(0));
        if (initialTaskFactory == null)
            throw new IllegalArgumentException("Expected at least one task factory in the task pipeline");

        pipeline.setTaskProgression(progression.toArray(new TaskId[0]));

        // Initial file types
        pipeline._initialFileTypesFromTask = true;
        pipeline._initialFileTypes = initialTaskFactory.getInputTypes();

        // Allow trigger configuration by default
        pipeline._allowForTriggerConfiguration = !xpipeline.isSetTriggerConfiguration() || xpipeline.getTriggerConfiguration().getAllow();

        // Misconfiguration: the user will never be able to start this pipeline
        if (pipeline._initialFileTypes == null || pipeline._initialFileTypes.isEmpty())
            throw new IllegalArgumentException("File analysis pipelines require at least one initial file type.");

        // CONSIDER: Attempt to map outputs from previous task to inputs of the next task

        // UNDONE: I don't understand the typeHierarchy
        // Add the initial types to the hierarchy
        pipeline._typeHierarchy = new HashMap<>();
        for (FileType ft : pipeline._initialFileTypes)
            pipeline._typeHierarchy.put(ft, Collections.emptyList());

//        // UNDONE: Default display state
//        if (xpipeline.isSetDefaultDisplay())
//            pipeline._defaultDisplayState = PipelineActionConfig.displayState.valueOf(xpipeline.getDefaultDisplayState());

        // CONSIDER: Infer 'splittable' from the TaskPath 'splitFiles' flag
        pipeline._splittable = false;
        if (xpipeline.isSetSplittable())
            pipeline._splittable = xpipeline.getSplittable();

        // For now, only write out the job info file for file-based pipeline jobs.
        pipeline._writeJobInfoFile = true;

        if (xpipeline.isSetCustomFields())
        {
            XmlObject[] xCustomFields = xpipeline.getCustomFields().selectPath("./*");
            List<Field<?>> customFields = new ArrayList<>();

            for (XmlObject xCustomField : xCustomFields)
            {
                if (xCustomField instanceof org.labkey.pipeline.xml.TextField xField)
                {
                    TextField field = new TextField(xField.getName(), xField.getLabel(), xField.getPlaceholder(), xField.getRequired(), xField.getDefaultValue(), xField.getHelpText(), xField.getHelpTextHref());
                    customFields.add(field);
                }
                else if (xCustomField instanceof org.labkey.pipeline.xml.TextAreaField xField)
                {
                    TextareaField field = new TextareaField(xField.getName(), xField.getLabel(), xField.getPlaceholder(), xField.getRequired(), xField.getDefaultValue(), xField.getHelpText(), xField.getHelpTextHref());
                    customFields.add(field);
                }
                else if (xCustomField instanceof org.labkey.pipeline.xml.NumberField xField)
                {
                    NumberField field = new NumberField(xField.getName(), xField.getLabel(), xField.getPlaceholder(), xField.getRequired(), xField.getDefaultValue(), xField.getHelpText(), xField.getHelpTextHref());
                    customFields.add(field);
                }
                else if (xCustomField instanceof org.labkey.pipeline.xml.CheckboxField xField)
                {
                    CheckboxField field = new CheckboxField(xField.getName(), xField.getLabel(), xField.getRequired(), xField.getDefaultValue(), xField.getHelpText(), xField.getHelpTextHref());
                    customFields.add(field);
                }
                else if (xCustomField instanceof org.labkey.pipeline.xml.RadioField xField)
                {
                    List<Option<String>> options = new ArrayList<>();
                    for (org.labkey.pipeline.xml.Option option : xField.getOptionArray())
                    {
                        options.add(new Option<>(option.getValue(), option.getLabel()));
                    }
                    RadioField<String> field = new RadioField<>(xField.getName(), xField.getLabel(), xField.getRequired(), xField.getDefaultValue(), options, xField.getHelpText(), xField.getHelpTextHref());
                    customFields.add(field);
                }
                else if (xCustomField instanceof org.labkey.pipeline.xml.SelectField xField)
                {
                    List<Option<String>> options = new ArrayList<>();
                    for (org.labkey.pipeline.xml.Option option : xField.getOptionArray())
                    {
                        options.add(new Option<>(option.getValue(), option.getLabel()));
                    }
                    SelectField<?> field = new SelectField<>(xField.getName(), xField.getLabel(), xField.getPlaceholder(), xField.getRequired(), xField.getDefaultValue(), options, xField.getHelpText(), xField.getHelpTextHref());
                    customFields.add(field);
                }
            }
            pipeline.setCustomFields(customFields);
        }

        //PipelineJobService.get().addTaskPipeline(pipeline);
        return pipeline;
    }

    private static TaskId createLocalTaskId(TaskId pipelineTaskId, String name)
    {
        String taskName = pipelineTaskId.getName() + TaskPipelineRegistry.LOCAL_TASK_PREFIX + name;
        return new TaskId(pipelineTaskId.getModuleName(), TaskId.Type.task, taskName, pipelineTaskId.getVersion());
    }

    @Override
    public FormSchema getFormSchema()
    {
        List<Field<?>> fields = new ArrayList<>(_defaultFields);

        if (!isMoveAvailable())
        {
            // Don't include moveToContainer or moveToSubdirectory if move is unavailable.
            fields.subList(4, 6).clear();
        }

        return new FormSchema(fields);
    }

    @Override
    public FormSchema getCustomFieldsFormSchema()
    {
        if (getCustomFields() != null)
            return new FormSchema(getCustomFields());

        return null;
    }

    /*
    private static TaskFactorySettings createSettings(TaskId pipelineTaskId, TaskFactory factory, XmlObject xsettings)
    {
        TaskId parentTaskId = factory.getId();
        TaskId taskId = createLocalTaskId(pipelineTaskId, parentTaskId.getName());

        TaskFactorySettings settings = createFactorySettings(factory, taskId);

        // UNDONE: I'm tired. Need to set the values on the settings reflectivly using xml->bean stuff
        if (settings instanceof org.labkey.api.assay.pipeline.AssayImportRunTaskFactorySettings)
        {
            org.labkey.api.assay.pipeline.AssayImportRunTaskFactorySettings airtfs = (org.labkey.api.assay.pipeline.AssayImportRunTaskFactorySettings)settings;

            XmlObject xproviderName = xsettings.selectChildren("http://labkey.org/pipeline/xml", "providerName")[0];
            String providerName = ((XmlObjectBase)xproviderName).getStringValue();
            airtfs.setProviderName(providerName);

            XmlObject xprotocolName = xsettings.selectChildren("http://labkey.org/pipeline/xml", "protocolName")[0];
            String protocolName = ((XmlObjectBase)xprotocolName).getStringValue();
            airtfs.setProtocolName(protocolName);
        }

        return settings;
    }

    // Reflection hack to create instance of appropriate TaskFactorySettings class
    private static TaskFactorySettings createFactorySettings(TaskFactory factory, TaskId taskId)
    {
        Class clazz = factory.getClass();

        // Look for a "configure" method up the class hierarchy
        Class<? extends TaskFactorySettings> typeBest = null;
        while (clazz != null)
        {
            for (Method m : clazz.getDeclaredMethods())
            {
                if (m.getName().equals("configure") || m.getName().equals("cloneAndConfigure"))
                {
                    Class[] types = m.getParameterTypes();
                    if (types.length != 1)
                        continue;

                    Class typeCurrent = types[0];
                    if (!TaskFactorySettings.class.isAssignableFrom(typeCurrent))
                        continue;

                    if (typeBest == null || typeBest.isAssignableFrom(typeCurrent))
                        typeBest = typeCurrent.asSubclass(TaskFactorySettings.class);
                }
            }

            clazz = clazz.getSuperclass();
        }

        if (typeBest == null)
            throw new IllegalArgumentException("TaskFactory settings class not found for type: " + clazz.getName());

        try
        {
            Constructor<? extends TaskFactorySettings> ctor = typeBest.getConstructor(TaskId.class);
            return ctor.newInstance(taskId);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalArgumentException("Error creating TaskFactorySettings: " + typeBest.getName(), e);
        }
    }
    */
}
