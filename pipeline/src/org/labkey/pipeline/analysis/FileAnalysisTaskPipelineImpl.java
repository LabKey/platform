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
    private Map<FileType, FileType[]> _typeHeirarchy;

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
        if (extHierarchy != null || _typeHeirarchy == null)
            _typeHeirarchy = new HashMap<FileType, FileType[]>();

        // Add the initial types to the hierarchy
        for (FileType ft : _initialFileTypes)
            _typeHeirarchy.put(ft, new FileType[0]);

        if (extHierarchy != null)
        {
            for (Map.Entry<FileType, List<FileType>> entry  : extHierarchy.entrySet())
            {
                List<FileType> inputExtList = entry.getValue();
                FileType[] hierarchy = inputExtList.toArray(new FileType[inputExtList.size()]);
                _typeHeirarchy.put(entry.getKey(), hierarchy);
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
     * Look at the specified type hierarchy to see if the requested file is an
     * ancestor to this processing job, residing outside the analysis directory.
     *
     * @param dirRoot The pipeline root directory, outside which no files can be processed.
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
                {
                    File expectedFile = new File(new File(uriData), name);
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
        }

        return null;
    }

    /**
     * Starting from the expectedDir, look up the chain until getting to the final directory. Return the first
     * file that matches by name.
     * @param expectedDir where we would have expected the file to be, but it wasn't there
     * @param dir must be a descendent of expectedDir, this is the deepest directory that will be inspected
     * @param name name of the file to look for
     * @return matching file, or null if nothing was found
     */
    private File findFileInAlternateDirectory(File expectedDir, File dir, String name)
    {
        // Bail out if we've gotten all the way down to the originally expected file location
        if (dir.equals(expectedDir))
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
