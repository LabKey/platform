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
package org.labkey.api.pipeline.file;

import org.labkey.api.formSchema.Field;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.util.FileType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisTaskPipelineSpec</code> is used with Spring
 * configuration to specify settings necessary for creating a
 * <code>FileAnalysisTaskPipeline</code>.
 */
public class FileAnalysisTaskPipelineSettings extends TaskPipelineSettings
{
    /**
     * Name to use in the <code>TaskId</code> for clone and configure.
     */
    private String _cloneName;

    /**
     * Text shown in the user interface for initiating the pipeline.
     */
    private String _description;

    /**
     * Name to be used by protocol factory for this pipeline.  This name
     * is used to create the analysis parent directory in the data directory
     * <p>
     * e.g. &lt;data&gt;/xtandem/protocol1
     * <p>
     * It is also used to create the protocols store directory for this type
     * under the system directory.
     */
    private String _protocolFactoryName;

    /**
     * Whether an initial list of file input extensions are required for the pipeline
     */
    private boolean _initialInputExtsRequired = true;

    /**
     * Extensions used as filters for files to show in the user interface
     * when initiating the pipeline.
     */
    private List<FileType> _initialInputExts;

    /**
     * Filter for files that match for the given pipeline.
     */
    private FileAnalysisTaskPipeline.FilePathFilter _initialInputFileFilter;

    /**
     * Maps the extension for a specific input/output file type to the list of
     * extensions for types from which it was derived.
     * <p>
     * e.g. <pre>
     * .mzXML => (.pep.xml, .features.tsv)
     * </pre>
     * <p>
     * In the example above, a file of the current primary input type,
     * .features.tsv, was derived by running a file analysis on a .pep.xml
     * file, which was in turn derived by running a file analysis on a
     * .mzXML file.
     * <p>
     * To find the .mzXML file in question, this chain must be walked
     * in reverse.
     */
    private Map<FileType, List<FileType>> _fileExtHierarchy;

    /** URL at which this job should be configured and launched */
    private String _analyzeURL;
    /** If set, the default location for the action in the UI */
    private PipelineActionConfig.displayState _defaultDisplayState;
    /** Set to true to allow this FileAnalysisTaskPipeline to be used with pipeline.TriggerConfigurations */
    private boolean _allowForTriggerConfiguration;
    /** Additional help text to assist the user in configuring their pipeline */
    private String _helpText;

    /** Whether to allow the task to move files during file analysis. Default is true */
    private Boolean _moveAvailable;

    /**
     * The custom fields to render when configuring a FileAnalysisTaskPipeline
     */
    private List<Field<?>> _customFields;

    public FileAnalysisTaskPipelineSettings(String name)
    {
        super(FileAnalysisTaskPipeline.class, name);
    }

    public FileAnalysisTaskPipelineSettings(Class<?> namespaceClass, String name)
    {
        super(namespaceClass, name);
    }

    @Override
    public TaskId getCloneId()
    {
        return new TaskId(FileAnalysisTaskPipeline.class, _cloneName);
    }

    public String getCloneName()
    {
        return _cloneName;
    }

    public void setCloneName(String cloneName)
    {
        _cloneName = cloneName;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getProtocolFactoryName()
    {
        return _protocolFactoryName;
    }

    public void setProtocolFactoryName(String protocolFactoryName)
    {
        _protocolFactoryName = protocolFactoryName;
    }

    public Boolean isInitialInputExtsRequired()
    {
        return _initialInputExtsRequired;
    }

    public void setInitialInputExtsRequired(boolean initialInputExtsRequired)
    {
        _initialInputExtsRequired = initialInputExtsRequired;
    }

    public List<FileType> getInitialInputExts()
    {
        return _initialInputExts;
    }

    public void setInitialInputExts(List<FileType> initialInputExts)
    {
        _initialInputExts = initialInputExts;
    }

    public void setInitialInputExt(FileType fileType)
    {
        _initialInputExts = new ArrayList<>();
        _initialInputExts.add(fileType);
    }

    public FileAnalysisTaskPipeline.FilePathFilter getInitialInputFileFilter()
    {
        return _initialInputFileFilter;
    }

    public void setInitialInputFileFilter(FileAnalysisTaskPipeline.FilePathFilter initialInputFileFilter)
    {
        _initialInputFileFilter = initialInputFileFilter;
    }

    public Map<FileType, List<FileType>> getFileExtHierarchy()
    {
        return _fileExtHierarchy;
    }

    public void setFileExtHierarchy(Map<FileType, List<FileType>> fileExtHierarchy)
    {
        _fileExtHierarchy = fileExtHierarchy;
    }

    public String getAnalyzeURL()
    {
        return _analyzeURL;
    }

    public void setAnalyzeURL(String url)
    {
        _analyzeURL = url;
    }

    public PipelineActionConfig.displayState getDefaultDisplayState()
    {
        return _defaultDisplayState;
    }

    public void setDefaultDisplayState(PipelineActionConfig.displayState defaultDisplayState)
    {
        _defaultDisplayState = defaultDisplayState;
    }

    public boolean isAllowForTriggerConfiguration()
    {
        return _allowForTriggerConfiguration;
    }

    public void setAllowForTriggerConfiguration(boolean allowForTriggerConfiguration)
    {
        _allowForTriggerConfiguration = allowForTriggerConfiguration;
    }

    public String getHelpText()
    {
        return _helpText;
    }

    public void setHelpText(String helpText)
    {
        _helpText = helpText;
    }

    public Boolean isMoveAvailable()
    {
        return _moveAvailable;
    }

    public void setMoveAvailable(Boolean moveAvailable)
    {
        _moveAvailable = moveAvailable;
    }

    public List<Field<?>> getCustomFields()
    {
        return _customFields;
    }

    public void setCustomFields(List<Field<?>> customFields)
    {
        _customFields = customFields;
    }
}
