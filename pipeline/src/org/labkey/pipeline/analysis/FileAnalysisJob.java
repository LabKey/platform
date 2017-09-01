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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisJob</code>
 */
public class FileAnalysisJob extends AbstractFileAnalysisJob
{
    private TaskId _taskPipelineId;
    private Map<String, String> _variableMap;

    private static final Logger LOG = Logger.getLogger(FileAnalysisJob.class);

    public FileAnalysisJob(FileAnalysisProtocol protocol,
                           String providerName,
                           ViewBackgroundInfo info,
                           PipeRoot root,
                           TaskId taskPipelineId,
                           String protocolName,
                           File fileParameters,
                           List<File> filesInput,
                           @Nullable Map<String, String> variableMap,
                           boolean splittable,
                           boolean writeJobInfoFile) throws IOException
    {
        super(protocol, providerName, info, root, protocolName, fileParameters, filesInput, splittable, writeJobInfoFile);

        _taskPipelineId = taskPipelineId;
        _variableMap = variableMap;
    }

    public FileAnalysisJob(FileAnalysisJob job, File fileInput)
    {
        super(job, fileInput);

        _taskPipelineId = job._taskPipelineId;
        _variableMap = job._variableMap;
    }

    @Override
    public String getDescription()
    {
        String description = getParameters().get("pipelineDescription");
        if(description != null)
            return description;

        return super.getDescription();
    }

    @Override
    public Map<String, String> getParameters()
    {
        Map<String, String> parameters = new HashMap<>(super.getParameters());
        if (_variableMap != null && !_variableMap.isEmpty())
            parameters.putAll(_variableMap);

        return Collections.unmodifiableMap(parameters);
    }

    public TaskId getTaskPipelineId()
    {
        return _taskPipelineId;
    }

    public AbstractFileAnalysisJob createSingleFileJob(File file)
    {
        return new FileAnalysisJob(this, file);
    }

    public FileAnalysisTaskPipeline getTaskPipeline()
    {
        TaskPipeline tp = super.getTaskPipeline();
        if (tp == null)
        {
            LOG.warn("Task pipeline " + _taskPipelineId + " not found.");
        }
        return (FileAnalysisTaskPipeline) tp; 
    }

    public File findInputFile(String name)
    {
        return findFile(name);
    }

    public File findOutputFile(String name)
    {
        return findFile(name);
    }

    /**
     * Look at the specified type hierarchy to see if the requested file is an
     * ancestor to this processing job, residing outside the analysis directory.
     *
     * @param name The name of the file to be located
     * @return The file location outside the analysis directory, or null, if no such match is found.
     */
    public File findFile(String name)
    {
        File dirAnalysis = getAnalysisDirectory();

        for (Map.Entry<FileType, List<FileType>> entry : getTaskPipeline().getTypeHierarchy().entrySet())
        {
            if (entry.getKey().isType(name))
            {
                // TODO: Eventually we will need to actually consult the parameters files
                //       in order to find files.

                // First try to go two directories up
                File dir = dirAnalysis.getParentFile();
                if (dir != null)
                {
                    dir = dir.getParentFile();
                }

                List<FileType> derivedTypes = entry.getValue();
                for (int i = derivedTypes.size() - 1; i >= 0; i--)
                {
                    // Go two directories up for each level of derivation
                    if (dir != null)
                    {
                        dir = dir.getParentFile();
                    }
                    if (dir != null)
                    {
                        dir = dir.getParentFile();
                    }
                }

                String relativePath = getPipeRoot().relativePath(dir);
                File expectedFile = getPipeRoot().resolvePath(relativePath + "/" + name);

                if (!NetworkDrive.exists(expectedFile))
                {
                    // If the file isn't where we would expect it, check other directories in the same hierarchy
                    File alternateFile = findFileInAlternateDirectory(expectedFile.getParentFile(), dirAnalysis, name);
                    if (alternateFile != null)
                    {
                        // If we found a file that matches, use it
                        return alternateFile;
                    }
                }
                return expectedFile;
            }
        }

        // Path of last resort is always to look in the current directory.
        return new File(dirAnalysis, name);
    }

    /**
     * Starting from the expectedDir, look up the chain until getting to the final directory. Return the first
     * file that matches by name.
     * @param expectedDir where we would have expected the file to be, but it wasn't there
     * @param dir must be a descendant of expectedDir, this is the deepest directory that will be inspected
     * @param name name of the file to look for
     * @return matching file, or null if nothing was found
     */
    private File findFileInAlternateDirectory(File expectedDir, File dir, String name)
    {
        // Bail out if we've gotten all the way down to the originally expected file location
        if (dir == null || dir.equals(expectedDir))
        {
            return null;
        }
        // Recurse through the parent directories to find it in the place closest to the expected directory
        File result = findFileInAlternateDirectory(expectedDir, dir.getParentFile(), name);
        if (result != null)
        {
            // If we found a match, use it
            return result;
        }

        result = new File(dir, name);
        if (NetworkDrive.exists(result))
        {
            return result;
        }
        return null;
    }
}
