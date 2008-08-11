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

import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.pipeline.file.FileAnalysisXarGeneratorSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.URIUtil;
import org.labkey.pipeline.api.TaskPipelineImpl;

import java.io.File;
import java.io.FileFilter;
import java.net.URI;
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
    private boolean _initialFileTypesFromTask;
    private FileType[] _initialFileTypes;
    private Map<FileType, FileType[]> _typeHeirarchy;
    private FileAnalysisXarGeneratorSupport _xarGeneratorSupport;

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

        if (settings.getXarGeneratorSupport() != null)
            _xarGeneratorSupport = settings.getXarGeneratorSupport();

        // Convert any input filter extensions to array of file types.
        List<String> inputFilterExts = settings.getInitialInputExts();
        if (inputFilterExts != null)
        {
            _initialFileTypesFromTask = false;
            _initialFileTypes = new FileType[inputFilterExts.size()];
            for (int i = 0; i < _initialFileTypes.length; i++)
                _initialFileTypes[i] = new FileType(inputFilterExts.get(i));
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
                throw new IllegalArgumentException("File analysis pipelines require at least on initial file type.");

        // Convert any input extension heirarchy into file types.
        Map<String, List<String>> extHeirarchy = settings.getFileExtHierarchy();
        if (extHeirarchy != null || _typeHeirarchy == null)
            _typeHeirarchy = new HashMap<FileType, FileType[]>();

        // Add the initial types to the heirarchy
        for (FileType ft : _initialFileTypes)
            _typeHeirarchy.put(ft, new FileType[0]);

        if (extHeirarchy != null)
        {
            if (_typeHeirarchy == null)
                _typeHeirarchy = new HashMap<FileType, FileType[]>();
            for (Map.Entry<String, List<String>> entry  : extHeirarchy.entrySet())
            {
                List<String> inputExtList = entry.getValue();
                FileType[] heirarchy = new FileType[inputExtList.size()];
                for (int i = 0; i < inputExtList.size(); i++)
                    heirarchy[i] = new FileType(inputExtList.get(i));
                _typeHeirarchy.put(new FileType(entry.getKey()), heirarchy);
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

    public FileAnalysisXarGeneratorSupport getXarGeneratorSupport()
    {
        return _xarGeneratorSupport;
    }

    public File findInputFile(FileAnalysisJobSupport support, String name)
    {
        return findFile(support.getRootDir(), support.getAnalysisDirectory(), name);
    }

    public File findInputFile(File dirRoot, File dirAnalysis, String name)
    {
        return findFile(dirRoot, dirAnalysis, name);
    }

    public File findOutputFile(FileAnalysisJobSupport support, String name)
    {
        return findFile(support.getRootDir(), support.getAnalysisDirectory(), name);
    }

    /**
     * Finds a file by name for a task in a <code>FileAnalysisTaskPipeline</code>.
     * Finding input and output files used to be very different, with one looking
     * at a list of shared files, and another a full type heirarchy.  In the end,
     * it seems simplest to have everything refer to the type heirarchy.
     * <p>
     * It may be possible one day to remove the rest of the input v. output
     * complexity.
     * 
     * @param dirRoot The pipeline root directory, outside which access is not allowed
     * @param dirAnalysis The analysis directory where most generated files end up
     * @param name The name of the file to locate
     * @return A file that specifically locates a processing input or output
     */
    private File findFile(File dirRoot, File dirAnalysis, String name)
    {
        File file = findAncestorFile(dirRoot, dirAnalysis, name);
        if (file != null)
            return file;

        // Path of last resort is always to look in the current directory.
        return new File(dirAnalysis, name);
    }

    /**
     * Look at the specified type heirarchy to see if the requested file is an
     * ancestor to this processing job, residing outside the analysis directory.
     *
     * @param dirRoot The pipeline root directoy, outside which no files can be processed.
     * @param dirAnalysis Default input/output directory for the current job.
     * @param name The name of the file to be located
     * @return The file location outside the analysis directory, or null, if no such match is found.
     */
    private File findAncestorFile(File dirRoot, File dirAnalysis, String name)
    {
        for (Map.Entry<FileType, FileType[]> entry : _typeHeirarchy.entrySet())
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

        return null;
    }
}
