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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    protected Integer _experimentRunRowId;
    private String _protocolName;
    private String _joinedBaseName;
    private String _baseName;
    private Path _dirData;
    private Path _dirAnalysis;
    private Path _fileParameters;
    private Path _fileJobInfo;
    private List<Path> _filesInput;
    private List<FileType> _inputTypes;
    private boolean _splittable = true;

    private Map<String, String> _parametersDefaults;
    private Map<String, String> _parametersOverrides;

    public static final String ANALYSIS_PARAMETERS_ROLE_NAME = "AnalysisParameters";

    // For serialization
    protected AbstractFileAnalysisJob() {}

    @Deprecated //Prefer the Path version, retained for backwards compatbility
    public AbstractFileAnalysisJob(AbstractFileAnalysisProtocol protocol,
                                   String providerName,
                                   ViewBackgroundInfo info,
                                   PipeRoot root,
                                   String protocolName,
                                   File fileParameters,
                                   List<File> filesInput,
                                   boolean splittable,
                                   boolean writeJobInfoFile) throws IOException
    {
        this(
                protocol,
                providerName,
                info,
                root,
                protocolName,
                fileParameters.toPath(),
                filesInput.stream().map(File::toPath).collect(Collectors.toList()),
                splittable,
                writeJobInfoFile
        );
    }

    public AbstractFileAnalysisJob(@NotNull AbstractFileAnalysisProtocol protocol,
                                   String providerName,
                                   ViewBackgroundInfo info,
                                   PipeRoot root,
                                   String protocolName,
                                   Path fileParameters,
                                   List<Path> filesInput,
                                   boolean splittable,
                                   boolean writeJobInfoFile) throws IOException
    {
        super(providerName, info, root);

        _filesInput = filesInput;
        _inputTypes = FileType.findTypes(protocol.getInputTypes(), _filesInput);
        _dirData = filesInput.get(0).getParent();
        _protocolName = protocolName;

        _fileParameters = fileParameters;
        getActionSet().add(_fileParameters, ANALYSIS_PARAMETERS_ROLE_NAME); // input
        _dirAnalysis = _fileParameters.getParent();

        // Load parameter files
        _parametersOverrides = getInputParameters().getInputParameters();

        // Check for explicitly set default parameters.  Otherwise use the default.
        String paramDefaults = _parametersOverrides.get("list path, default parameters");
        Path fileDefaults;
        if (paramDefaults != null)
            fileDefaults = getPipeRoot().resolveToNioPath(paramDefaults);
        else
            fileDefaults = protocol.getFactory().getDefaultParametersFile(root);

        _parametersDefaults = getInputParameters(fileDefaults).getInputParameters();

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
            _baseName = protocol.getBaseName(_filesInput.get(0));
        }

        String logFile = protocol.timestampLog() ? FileUtil.makeFileNameWithTimestamp(_baseName) : _baseName;
        setupLocalDirectoryAndJobLog(getPipeRoot(), "FileAnalysis", logFile);
    }

    /**
     * @return Path String for a local working directory, temporary if root is cloud based
     */
    @Override
    protected Path getWorkingDirectoryString()
    {
        return _dirAnalysis.toAbsolutePath();
    }

    public AbstractFileAnalysisJob(AbstractFileAnalysisJob job, File fileInput)
    {
        this(job, Collections.singletonList(fileInput));
    }

    public AbstractFileAnalysisJob(AbstractFileAnalysisJob job, List<File> filesInput)
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
        _filesInput = filesInput.stream().map(File::toPath).collect(Collectors.toList());
        _inputTypes = FileType.findTypes(job._inputTypes, _filesInput);
        _baseName = (_inputTypes.isEmpty() ? filesInput.get(0).getName() : _inputTypes.get(0).getBaseName(filesInput.get(0)));

        setupLocalDirectoryAndJobLog(getPipeRoot(), "FileAnalysis", _baseName);
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
        return _splittable && getInputFilePaths().size() > 1;
    }

    @Override
    public List<PipelineJob> createSplitJobs()
    {
        if (getInputFiles().size() == 1)
            return super.createSplitJobs();

        ArrayList<PipelineJob> jobs = new ArrayList<>();
        for (File file : getInputFiles())
            jobs.add(createSingleFileJob(file));
        return Collections.unmodifiableList(jobs);
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(getTaskPipelineId());
    }

    abstract public TaskId getTaskPipelineId();

    abstract public AbstractFileAnalysisJob createSingleFileJob(File file);

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
        for (Path fileInput : _filesInput)
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
            for (Path fileInput : _filesInput)
            {
                if (fileType.isType(fileInput))
                    return fileType.getBaseName(fileInput);
            }
        }

        return getBaseName();
    }

    @Override
    public File getDataDirectory()
    {
        return _dirData.toFile();
    }

    @Override
    public Path getDataDirectoryPath()
    {
        return _dirData;
    }

    @Override
    public File getAnalysisDirectory()
    {
        return _dirAnalysis.toFile();
    }

    @Override
    public Path getAnalysisDirectoryPath()
    {
        return _dirAnalysis;
    }

    @Override
    public File findOutputFile(@NotNull String outputDir, @NotNull String fileName)
    {
        return getOutputFile(outputDir, fileName, getPipeRoot(), getLogger(), getAnalysisDirectory());
    }

    public static File getOutputFile(@NotNull String outputDir, @NotNull String fileName, PipeRoot root, Logger log, File analysisDirectory)
    {
        File dir;
        if (outputDir.startsWith("/"))
        {
            dir = root.resolvePath(outputDir);
            if (dir == null)
                throw new RuntimeException("Output directory not under pipeline root: " + outputDir);

            if (!NetworkDrive.exists(dir))
            {
                log.info("Creating output directory under pipeline root: " + dir);
                if (!dir.mkdirs())
                    throw new RuntimeException("Failed to create output directory under pipeline root: " + outputDir);
            }
        }
        else
        {
            dir = new File(analysisDirectory, outputDir);
            if (!NetworkDrive.exists(dir))
            {
                log.info("Creating output directory under pipeline analysis dir: " + dir);
                if (!dir.mkdirs())
                    throw new RuntimeException("Failed to create output directory under analysis dir: " + outputDir);
            }
        }

        return new File(dir, fileName);
    }

    @Override
    public List<File> getInputFiles()
    {
        return getInputFilePaths().stream().map(Path::toFile).collect(Collectors.toList());
    }

    @Override
    public List<Path> getInputFilePaths()
    {
        return _filesInput;
    }

    @Override
    @Nullable
    public File getJobInfoFile()
    {
        return _fileJobInfo.toFile();
    }


    @Override
    @Nullable
    public Path getJobInfoFilePath()
    {
        return _fileJobInfo;
    }

    @Override
    public File getParametersFile()
    {
        return _fileParameters.toFile();
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

    public ParamParser getInputParameters(Path parametersFile) throws IOException
    {
        ParamParser parser = createParamParser();
        parser.parse(Files.newInputStream(parametersFile));
        if (parser.getErrors() != null)
        {
            ParamParser.Error err = parser.getErrors()[0];
            if (err.getLine() == 0)
            {
                throw new IOException("Failed parsing input xml '" + parametersFile.toString() + "'.\n" +
                        err.getMessage());
            }
            else
            {
                throw new IOException("Failed parsing input xml '" + parametersFile.toString() + "'.\n" +
                        "Line " + err.getLine() + ": " + err.getMessage());
            }
        }
        return parser;
    }

    private void logParameters(String description, Path file, Map<String, String> parameters)
    {
        _log.debug(description + " " + parameters.size() + " parameters (" + file + "):");
        for (Map.Entry<String, String> entry : new TreeMap<>(parameters).entrySet())
            _log.debug(entry.getKey() + " = " + entry.getValue());
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
        return getDataDescription(getDataDirectoryPath(), getBaseName(), getJoinedBaseName(), getProtocolName(), getInputFiles());
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

    @Deprecated //prefer Path version
    public static String getDataDescription(File dirData, String baseName, String joinedBaseName, String protocolName)
    {
        return getDataDescription(dirData.toPath(), baseName, joinedBaseName, protocolName, Collections.emptyList());
    }

    public static String getDataDescription(Path dirData, String baseName, String joinedBaseName, String protocolName, List<File> inputFiles)
    {
        String dataName = "";
        if (dirData != null)
        {
            dataName = dirData.getFileName().toString();
            // Can't remember why we would ever need the "xml" check. We may get an extra "." in the path,
            // so check for that and remove it.
            if (".".equals(dataName) || "xml".equals(dataName))
            {
                dirData = dirData.getParent();
                if (dirData != null)
                    dataName = dirData.getFileName().toString();
            }
        }

        StringBuilder description = new StringBuilder(dataName);
        if (baseName != null && !baseName.equals(dataName) &&
                !(AbstractFileAnalysisProtocol.LEGACY_JOINED_BASENAME.equals(baseName) || baseName.equals(joinedBaseName)))   // For cluster
        {
            if (description.length() > 0)
                description.append("/");
            description.append(baseName);
        }
        description.append(" (").append(protocolName).append(")");

        // input files
        if (!inputFiles.isEmpty())
        {
            description.append(" (");
            description.append(String.join(",", inputFiles.stream().map(f -> f.getName()).collect(Collectors.toList())));
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
