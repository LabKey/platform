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

import io.micrometer.common.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * <code>AbstractFileAnalysisJob</code>
 */
abstract public class AbstractFileAnalysisJob extends PipelineJob implements FileAnalysisJobSupport
{
    private static final Logger _log = LogManager.getLogger(AbstractFileAnalysisJob.class);

    protected Long _experimentRunRowId;
    private String _protocolName;
    private String _joinedBaseName;
    private String _baseName;
    private FileLike _dirData;
    private FileLike _dirAnalysis;
    private FileLike _fileParameters;
    private List<FileLike> _filesInput;
    private List<FileType> _inputTypes;
    private boolean _splittable = true;

    private Map<String, String> _parametersDefaults;
    private Map<String, String> _parametersOverrides;

    public static final String ANALYSIS_PARAMETERS_ROLE_NAME = "AnalysisParameters";

    // For serialization
    protected AbstractFileAnalysisJob() {}

    public AbstractFileAnalysisJob(@NotNull AbstractFileAnalysisProtocol<?> protocol,
                                   String providerName,
                                   ViewBackgroundInfo info,
                                   PipeRoot root,
                                   String protocolName,
                                   FileLike fileParameters,
                                   List<FileLike> filesInput,
                                   boolean splittable) throws IOException
    {
        super(providerName, info, root);

        _filesInput = filesInput;
        _inputTypes = FileType.findTypes(protocol.getInputTypes(), _filesInput);
        _dirData = filesInput.getFirst().getParent();
        _protocolName = protocolName;

        _fileParameters = fileParameters;
        getActionSet().add(_fileParameters, ANALYSIS_PARAMETERS_ROLE_NAME); // input
        _dirAnalysis = _fileParameters.getParent();

        // Load parameter files
        _parametersOverrides = getInputParameters().getInputParameters();

        // Check for explicitly set default parameters.  Otherwise use the default.
        String paramDefaults = _parametersOverrides.get("list path, default parameters");
        FileLike fileDefaults;
        if (paramDefaults != null)
            fileDefaults = getPipeRoot().resolvePathToFileLike(paramDefaults);
        else
            fileDefaults = protocol.getFactory().getDefaultParametersFile(root);

        _parametersDefaults = fileDefaults != null && fileDefaults.exists() ?
                getInputParameters(fileDefaults).getInputParameters() :
                Collections.emptyMap();

        if (_log.isDebugEnabled())
        {
            logParameters("Defaults", fileDefaults, _parametersDefaults);
            logParameters("Overrides", fileParameters, _parametersOverrides);
        }

        _splittable = splittable;
        _joinedBaseName = protocol.getJoinedBaseName();
        if (_filesInput.size() > 1)
        {
            _baseName = _joinedBaseName;
        }
        else
        {
            _baseName = protocol.getBaseName(_filesInput.getFirst());
        }

        String logFile = protocol.timestampLog() ? FileUtil.makeFileNameWithTimestamp(_baseName) : _baseName;
        setupLocalDirectoryAndJobLog(getPipeRoot(), logFile);
    }

    /**
     * @return Path String for a local working directory, temporary if root is cloud based
     */
    @Override
    protected FileLike getWorkingDirectoryString()
    {
        return _dirAnalysis;
    }

    public AbstractFileAnalysisJob(AbstractFileAnalysisJob job, FileLike fileInput)
    {
        this(job, Collections.singletonList(fileInput));
    }

    public AbstractFileAnalysisJob(AbstractFileAnalysisJob job, List<FileLike> filesInput)
    {
        super(job);

        // Copy some parameters from the parent job.
        _experimentRunRowId = job._experimentRunRowId;
        _protocolName = job._protocolName;
        _dirData = job._dirData;
        _dirAnalysis = job._dirAnalysis;
        _fileParameters = job._fileParameters;
        _parametersDefaults = job._parametersDefaults;
        _parametersOverrides = job._parametersOverrides;
        _splittable = job._splittable;
        _joinedBaseName = job._joinedBaseName;

        // Change parameters which are specific to the fraction job.
        _filesInput = new ArrayList<>(filesInput);
        _inputTypes = FileType.findTypes(job._inputTypes, _filesInput);
        _baseName = (_inputTypes.isEmpty() ? filesInput.getFirst().getName() : _inputTypes.getFirst().getBaseName(filesInput.getFirst()));

        setupLocalDirectoryAndJobLog(getPipeRoot(), _baseName);
    }

    @Override
    public void clearActionSet(ExpRun run)
    {
        super.clearActionSet(run);
        getActionSet().add(_fileParameters, ANALYSIS_PARAMETERS_ROLE_NAME);

        _experimentRunRowId = run.getRowId();
    }

    public void setSplittable(boolean splittable)
    {
        _splittable = splittable;
    }

    @Override
    public boolean isSplittable()
    {
        return _splittable && getInputFiles().size() > 1;
    }

    @Override
    public List<PipelineJob> createSplitJobs()
    {
        if (getInputFiles().size() == 1)
            return super.createSplitJobs();

        ArrayList<PipelineJob> jobs = new ArrayList<>();
        for (FileLike file : _filesInput)
            jobs.add(createSingleFileJob(file));
        return Collections.unmodifiableList(jobs);
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(getTaskPipelineId());
    }

    abstract public TaskId getTaskPipelineId();

    abstract public AbstractFileAnalysisJob createSingleFileJob(FileLike file);

    @Override
    public String getProtocolName()
    {
        return _protocolName;
    }

    @Override
    public String getBaseName()
    {
        return _baseName;
    }

    @Override
    public String getJoinedBaseName()
    {
        return _joinedBaseName;
    }

    @Override
    public List<String> getSplitBaseNames()
    {
        ArrayList<String> baseNames = new ArrayList<>();
        for (FileLike fileInput : _filesInput)
        {
            for (FileType ft : _inputTypes)
            {
                if (ft.isType(fileInput))
                {
                    baseNames.add(ft.getBaseName(fileInput));
                    break;
                }
            }
        }
        return baseNames;
    }

    @Override
    public String getBaseNameForFileType(FileType fileType)
    {
        if (fileType != null)
        {
            for (FileLike fileInput : _filesInput)
            {
                if (fileType.isType(fileInput))
                    return fileType.getBaseName(fileInput);
            }
        }

        return getBaseName();
    }

    @Override
    public FileLike getDataDirectory()
    {
        return _dirData;
    }

    @Override
    public FileLike getAnalysisDirectory()
    {
        return _dirAnalysis;
    }

    @Override
    public FileLike findOutputFile(@NotNull String outputDir, @NotNull String fileName)
    {
        return getOutputFile(outputDir, fileName, getPipeRoot(), getLogger(), getAnalysisDirectory());
    }

    public static FileLike getOutputFile(@NotNull String outputDir, @NotNull String fileName, PipeRoot root, Logger log, FileLike analysisDirectory)
    {
        FileLike dir;
        if (outputDir.startsWith("/"))
        {
            dir = root.resolvePathToFileLike(outputDir);
            if (dir == null)
                throw new RuntimeException("Output directory not under pipeline root: " + outputDir);

            if (!NetworkDrive.exists(dir))
            {
                log.info("Creating output directory under pipeline root: {}", dir);
                try
                {
                    dir.mkdirs();
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Failed to create output directory under pipeline root: " + outputDir, e);
                }
            }
        }
        else
        {
            dir = analysisDirectory.resolveChild(outputDir);
            if (!NetworkDrive.exists(dir))
            {
                log.info("Creating output directory under pipeline analysis dir: {}", dir);
                try
                {
                    dir.mkdirs();
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Failed to create output directory under analysis dir: " + outputDir, e);
                }
            }
        }

        return dir.resolveChild(fileName);
    }

    @Override
    public List<FileLike> getInputFiles()
    {
        return _filesInput;
    }

    @Override
    public FileLike getParametersFile()
    {
        return _fileParameters;
    }

    @Override
    public Map<String, String> getParameters()
    {
        HashMap<String, String> params = new HashMap<>(_parametersDefaults);
        params.putAll(_parametersOverrides);

        // Add previous output parameters to the current set
        for (RecordedAction action : getActionSet().getActions())
        {
            for (Map.Entry<RecordedAction.ParameterType, Object> entry : action.getOutputParams().entrySet())
            {
                RecordedAction.ParameterType p = entry.getKey();
                Object value = entry.getValue();
                if (p.getType() != PropertyType.ATTACHMENT)
                    params.put(p.getName(), Objects.toString(value, null));
            }
        }

        return Collections.unmodifiableMap(params);
    }

    public ParamParser getInputParameters() throws IOException
    {
        return getInputParameters(_fileParameters);
    }

    public ParamParser getInputParameters(FileLike parametersFile) throws IOException
    {
        ParamParser parser = createParamParser();
        parser.parse(parametersFile.openInputStream());
        if (parser.getErrors() != null)
        {
            ParamParser.Error err = parser.getErrors()[0];
            if (err.getLine() == 0)
            {
                throw new IOException("Failed parsing input xml '" + parametersFile + "'.\n" +
                        err.getMessage());
            }
            else
            {
                throw new IOException("Failed parsing input xml '" + parametersFile + "'.\n" +
                        "Line " + err.getLine() + ": " + err.getMessage());
            }
        }
        return parser;
    }

    private void logParameters(String description, FileLike file, Map<String, String> parameters)
    {
        _log.debug("{} {} parameters ({}):", description, parameters.size(), file);
        for (Map.Entry<String, String> entry : new TreeMap<>(parameters).entrySet())
            _log.debug("{} = {}", entry.getKey(), entry.getValue());
        _log.debug("");
    }

    @Override
    public ParamParser createParamParser()
    {
        return PipelineJobService.get().createParamParser();
    }

    @Override
    public String getDescription()
    {
        return getDataDescription(getDataDirectory(), getBaseName(), getJoinedBaseName(), getProtocolName(), _filesInput);
    }

    @Override
    public ActionURL getStatusHref()
    {
        if (_experimentRunRowId != null)
        {
            ExpRun run = ExperimentService.get().getExpRun(_experimentRunRowId.intValue());
            if (run != null)
                return PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(run);
        }
        return null;
    }

    public static String getDataDescription(FileLike dirData, String baseName, String joinedBaseName, String protocolName, List<FileLike> inputFiles)
    {
        String dataName = "";
        if (dirData != null)
        {
            dataName = dirData.getName();
            // Can't remember why we would ever need the "xml" check. We may get an extra "." in the path,
            // so check for that and remove it.
            if (".".equals(dataName) || "xml".equals(dataName))
            {
                dirData = dirData.getParent();
                if (dirData != null)
                    dataName = dirData.getName();
            }
        }

        StringBuilder description = new StringBuilder(dataName);
        if (baseName != null && !baseName.equals(dataName) &&
                !(AbstractFileAnalysisProtocol.LEGACY_JOINED_BASENAME.equals(baseName) || baseName.equals(joinedBaseName)))   // For cluster
        {
            if (!description.isEmpty())
                description.append("/");
            description.append(baseName);
        }
        if (!StringUtils.isEmpty(protocolName))
        {
            description.append(" (").append(protocolName).append(")");
        }

        // input files
        if (!inputFiles.isEmpty())
        {
            description.append(" (");
            //p.getFileName returns the full S3 path -- S3fs bug?
            description.append(inputFiles.stream().map(FileLike::getName).collect(Collectors.joining(",")));
            description.append(")");
        }
        return description.toString();
    }

    /**
     * returns support level for .xml.gz handling
     * we always read .xml.gz, but may also have a
     * preference for producing it in the pipeline
     */
    @Override
    public FileType.gzSupportLevel getGZPreference()
    {
        String doGZ = getParameters().get("pipeline, gzip outputs");
        return "yes".equalsIgnoreCase(doGZ)?FileType.gzSupportLevel.PREFER_GZ:FileType.gzSupportLevel.SUPPORT_GZ;
    }
}
