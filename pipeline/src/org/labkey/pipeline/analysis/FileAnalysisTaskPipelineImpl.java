/*
 * Copyright (c) 2008 LabKey Software Foundation
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
import org.labkey.api.pipeline.file.FileAnalysisXarGeneratorSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.URIUtil;
import org.labkey.pipeline.api.TaskPipelineImpl;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisTaskPipelineImp</code>
 */
public class FileAnalysisTaskPipelineImpl extends TaskPipelineImpl implements FileAnalysisTaskPipeline, Cloneable
{
    /**
     * The text that will appear in the button to start this pipeline.
     */
    private String _description;
    private String _protocolFactoryName;
    private FileType[] _initialFileTypes;
    private FileType[] _sharedOutputTypes;
    private Map<FileType, FileType[]> _inputTypeHeirarchy;
    private FileAnalysisXarGeneratorSupport _xarGeneratorSupport;

    public FileAnalysisTaskPipelineImpl()
    {
        super(new TaskId(FileAnalysisTaskPipeline.class));
    }

    public TaskPipeline cloneAndConfigure(TaskPipelineSettings settings, TaskId[] taskProgression) throws CloneNotSupportedException
    {
        FileAnalysisTaskPipelineImpl pipeline = (FileAnalysisTaskPipelineImpl)
                super.cloneAndConfigure(settings, taskProgression);

        return pipeline.configure((FileAnalysisTaskPipelineSettings) settings);
    }

    private TaskPipeline configure(FileAnalysisTaskPipelineSettings settings)
    {
        if (settings.getDescription() != null)
            _description = settings.getDescription();

        if (settings.getProtocolFactoryName() != null)
            _protocolFactoryName = settings.getProtocolFactoryName();

        if (settings.getXarGeneratorSupport() != null)
            _xarGeneratorSupport = settings.getXarGeneratorSupport();

        // Convert any input filter extensions to array of file types.
        List<String> inputFilterExts = settings.getInitialInputExts();
        if (inputFilterExts != null)
        {
            _initialFileTypes = new FileType[inputFilterExts.size()];
            for (int i = 0; i < _initialFileTypes.length; i++)
                _initialFileTypes[i] = new FileType(inputFilterExts.get(i));
        }
        else if (getInitialFileTypes() == null)
        {
            TaskId tid = getTaskProgression()[0];
            TaskFactory factory = PipelineJobService.get().getTaskFactory(tid);
            FileType ft = factory.getInputType();
            if (ft != null)
                _initialFileTypes = new FileType[] { ft };
        }

        // Misconfiguration: the user will never be able to start this pipeline
        if (_initialFileTypes == null || _initialFileTypes.length == 0)
                throw new IllegalArgumentException("File analysis pipelines require at least on initial file type.");

        // Convert any shared output extensions to array of file types.
        List<String> sharedOutputExts = settings.getSharedOutputExts();
        if (sharedOutputExts == null)
        {
            if (_sharedOutputTypes == null)
                _sharedOutputTypes = new FileType[0];
        }
        else
        {
            _sharedOutputTypes = new FileType[sharedOutputExts.size()];
            for (int i = 0; i < _sharedOutputTypes.length; i++)
                _sharedOutputTypes[i] = new FileType(sharedOutputExts.get(i));            
        }

        _inputTypeHeirarchy = new HashMap<FileType, FileType[]>();

        // Add the initial types to the heirarchy
        for (FileType ft : _initialFileTypes)
            _inputTypeHeirarchy.put(ft, new FileType[0]);

        // Convert any input extension heirarchy into file types.
        Map<String, List<String>> inputExtHeirarchy = settings.getInputExtHeirarchy();
        if (inputExtHeirarchy != null)
        {
            if (_inputTypeHeirarchy == null)
                _inputTypeHeirarchy = new HashMap<FileType, FileType[]>();
            for (Map.Entry<String, List<String>> entry  : inputExtHeirarchy.entrySet())
            {
                List<String> inputExtList = entry.getValue();
                FileType[] heirarchy = new FileType[inputExtList.size()];
                for (int i = 0; i < inputExtList.size(); i++)
                    heirarchy[i] = new FileType(inputExtList.get(i));
                _inputTypeHeirarchy.put(new FileType(entry.getKey()), heirarchy);
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

    public boolean isInitialType(File f)
    {
        if (_initialFileTypes != null)
        {
            for (FileType ft : _initialFileTypes)
            {
                if (ft.isType(f))
                    return true;
            }
        }

        return false;
    }

    public FileAnalysisXarGeneratorSupport getXarGeneratorSupport()
    {
        return _xarGeneratorSupport;
    }

    public File findInputFile(FileAnalysisJobSupport support, String name)
    {
        return findInputFile(support.getRootDir(), support.getAnalysisDirectory(), name);
    }

    public File findInputFile(File dirRoot, File dirAnalysis, String name)
    {
        for (Map.Entry<FileType, FileType[]> entry : _inputTypeHeirarchy.entrySet())
        {
            if (entry.getKey().isType(name))
            {
                // TODO: Eventually we will need to actually consult the parameters files
                //       in order to find files.
                StringBuffer analysisRelativePath = new StringBuffer("../..");
                FileType[] derivedTypes = entry.getValue();
                for (int i = derivedTypes.length - 1; i >= 0; i--)
                    analysisRelativePath.append("/../..");
                URI uriData = URIUtil.resolve(dirRoot.toURI(),
                        dirAnalysis.toURI(),
                        analysisRelativePath.toString());

                if (uriData != null)
                    return new File(new File(uriData), name);
            }
        }

        // Path of last resort is always to look in the current directory.
        return new File(dirAnalysis, name);
    }

    public File findOutputFile(FileAnalysisJobSupport support, String name)
    {
        for (FileType ft : _sharedOutputTypes)
        {
            if (ft.isType(name))
                return new File(support.getDataDirectory(), name);
        }

        return new File(support.getAnalysisDirectory(), name);
    }
}
