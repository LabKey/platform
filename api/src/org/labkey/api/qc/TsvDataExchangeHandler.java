/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.api.qc;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUploadXarContext;
import org.labkey.api.assay.DefaultAssayRunCreator;
import org.labkey.api.assay.TsvDataHandler;
import org.labkey.api.assay.actions.AssayRunUploadForm;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.data.Container;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.PrintWriters;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

/*
* User: Karl Lum
* Date: Jan 7, 2009
* Time: 5:16:43 PM
*/
public class TsvDataExchangeHandler implements DataExchangeHandler
{
    public enum Props {
        assayId,                // the assay id from the run properties field
        runComments,            // run properties comments
        baseUrl,
        containerPath,
        assayType,              // assay definition name : general, nab, elispot etc.
        assayName,              // assay instance name
        userName,               // user email
        workingDir,             // temp directory that the script will be executed from
        protocolId,             // protocol row id
        protocolLsid,
        protocolDescription,

        runDataFile,
        runDataUploadedFile,
        errorsFile,
        transformedRunPropertiesFile,
        severityLevel,
        maximumSeverity,
        originalFileLocation;
    }
    public enum errLevel {
        NONE,
        WARN,
        ERROR
    }
    public static final String SAMPLE_DATA_PROP_NAME = "sampleData";
    public static final String VALIDATION_RUN_INFO_FILE = "runProperties.tsv";
    public static final String ERRORS_FILE = "validationErrors.tsv";
    public static final String RUN_DATA_FILE = "runData.tsv";
    public static final String TRANSFORMED_RUN_INFO_FILE = "transformedRunProperties.tsv";
    public static final String TRANS_ERR_FILE = "errors.html";

    private Map<String, String> _formFields = new HashMap<>();
    private Map<String, List<Map<String, Object>>> _sampleProperties = new HashMap<>();
    private static final Logger LOG = LogManager.getLogger(TsvDataExchangeHandler.class);
    private DataSerializer _serializer = new TsvDataSerializer();

    /** Files that shouldn't be considered part of the run's output, such as the transform script itself */
    private Set<File> _filesToIgnore = new HashSet<>();

    // Map to store the working directory to span across a transform warning. See directoryKey() for the String key.
    // Container is saved to ensure the user has access to the container and File is the actual working directory.
    private static final Map<String, Pair<Container, File>> workingDirectories = Collections.synchronizedMap(new HashMap<>());

    @Override
    public DataSerializer getDataSerializer()
    {
        return _serializer;
    }

    @Override
    public Pair<File, Set<File>> createTransformationRunInfo(AssayRunUploadContext<? extends AssayProvider> context, ExpRun run, File scriptDir, Map<DomainProperty, String> runProperties, Map<DomainProperty, String> batchProperties) throws Exception
    {
        File runProps = new File(scriptDir, VALIDATION_RUN_INFO_FILE);
        _filesToIgnore.add(runProps);

        try (PrintWriter pw = PrintWriters.getPrintWriter(runProps))
        {
            // serialize the batch and run properties to a tsv
            Map<DomainProperty, String> mergedProps = new HashMap<>(runProperties);
            mergedProps.putAll(batchProperties);

            // Hack to get TSV values to be properly quoted if they include tabs
            TSVWriter writer = createTSVWriter();
            writeRunProperties(context, mergedProps, scriptDir, pw, writer);

            // add the run data entries
            Set<File> dataFiles = writeRunData(context, run, scriptDir, pw);

            // any additional sample property sets
            for (Map.Entry<String, List<Map<String, Object>>> set : _sampleProperties.entrySet())
            {
                File sampleData = new File(scriptDir, set.getKey() + ".tsv");
                getDataSerializer().exportRunData(context.getProtocol(), set.getValue(), sampleData);

                pw.append(set.getKey());
                pw.append('\t');
                pw.println(sampleData.getAbsolutePath());
                _filesToIgnore.add(sampleData);
            }

            // errors file location
            File errorFile = new File(scriptDir, ERRORS_FILE);
            pw.append(Props.errorsFile.name());
            pw.append('\t');
            pw.println(errorFile.getAbsolutePath());

            _filesToIgnore.add(errorFile);

            // transformed run properties file location
            File transformedRunPropsFile = new File(scriptDir, TRANSFORMED_RUN_INFO_FILE);
            pw.append(Props.transformedRunPropertiesFile.name());
            pw.append('\t');
            pw.println(transformedRunPropsFile.getAbsolutePath());
            _filesToIgnore.add(transformedRunPropsFile);

            // error level initialization
            pw.append(Props.severityLevel.name());
            pw.append('\t');
            if(context instanceof AssayRunUploadForm && null != ((AssayRunUploadForm) context).getSeverityLevel())
                pw.println(((AssayRunUploadForm) context).getSeverityLevel());
            else
                pw.println(errLevel.WARN.name());

            return new Pair<>(runProps, dataFiles);
        }
    }

    /**
     * Writes out a tsv representation of the assay uploaded data.
     */
    protected Set<File> writeRunData(AssayRunUploadContext context, ExpRun run, File scriptDir, PrintWriter pw) throws Exception
    {
        TransformResult transform = context.getTransformResult();
        if (!transform.getTransformedData().isEmpty())
            return _writeTransformedRunData(context, transform, run, scriptDir, pw);
        else
            return _writeRunData(context, run, scriptDir, pw);
    }

    /**
     * Called to write out any uploaded run data in preparation for a validation or transform script.
     *
     * assay wizard import: uploadedData is used and rawData is null.
     * assay-saveAssayBatch.api: does not include uploadedData but may include an inputData file along with rawData rows.
     * assay-importRun.api: may include uploadedData or rawData (with or without an inputData file).
     */
    protected Set<File> _writeRunData(AssayRunUploadContext context, ExpRun run, File scriptDir, PrintWriter pw) throws Exception
    {
        List<File> result = new ArrayList<>();

        Map<String, File> uploadedData = context.getUploadedData();
        List<Map<String, Object>> rawData = context.getRawData();

        // For now, only one of uploadedData or rawData is used, not both at the same time.
        Collection<ExpData> dataInputs = Collections.emptyList();
        if (uploadedData.isEmpty() && rawData != null && !rawData.isEmpty())
        {
            dataInputs = run.getDataInputs().keySet();
        }

        boolean hasRunDataUploadedFile = !uploadedData.isEmpty() || !dataInputs.isEmpty();

        ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
        XarContext xarContext = new AssayUploadXarContext("Simple Run Creation", context);

        Map<DataType, List<Map<String, Object>>> mergedDataMap = new HashMap<>();

        // All of the DataTypes that support
        Set<DataType> transformDataTypes = new HashSet<>();

        DataType dataType = context.getProvider().getDataType();
        if (dataType == null)
            dataType = TsvDataHandler.RELATED_TRANSFORM_FILE_DATA_TYPE;

        String sep = "";
        if (hasRunDataUploadedFile)  // this makes sure not to print this property unless we're sure we'll print a value, or the test parser will complain later
        {
            pw.append(Props.runDataUploadedFile.name()).append('\t');
        }

        // include uploaded data files in the runProperties.tsv and read the data rows from the file
        for (File data : uploadedData.values())
        {
            result.add(data);
            ExpData expData = ExperimentService.get().createData(context.getContainer(), dataType, data.getName());
            expData.setRun(run);

            ExperimentDataHandler handler = expData.findDataHandler();
            if (handler instanceof ValidationDataHandler)
            {
                pw.append(sep).append(data.getAbsolutePath());
                sep = ";";
                _filesToIgnore.add(data);

                // for the data map sent to validation or transform scripts, we want to attempt type conversion, but if it fails, return
                // the original field value so the transform script can attempt to clean it up.
                DataLoaderSettings settings = new DataLoaderSettings();
                settings.setBestEffortConversion(true);
                settings.setAllowEmptyData(true);
                settings.setThrowOnErrors(false);

                Map<DataType, List<Map<String, Object>>> dataMap = ((ValidationDataHandler)handler).getValidationDataMap(expData, data, info, context.getLogger() != null ? context.getLogger() : LOG, xarContext, settings);

                // Combine the rows of any of the same DataTypes into a single entry
                addToMergedMap(mergedDataMap, dataMap);
                transformDataTypes.addAll(dataMap.keySet());
            }
        }

        // Issue 35262: when using assay-importRun.api with dataRows and there is a transform script, write out to a file
        // include dataInput files in the runProperties.tsv (only if there is rawData rows)
        for (ExpData inputData : dataInputs)
        {
            File file = inputData.getFile();
            pw.append(sep).append(file.getAbsolutePath());
            sep = ";";
            result.add(file);
        }

        if (hasRunDataUploadedFile)  // this avoids printing empty lines, which the test parser will complain about later
            pw.println();


        // Add rawData rows to mergedDataMap so it will be included as a runDataFile in the runProperties.tsv
        if (rawData != null && !rawData.isEmpty())
        {
            File runData = new File(scriptDir, RUN_DATA_FILE);
            result.add(runData);

            addToMergedMap(mergedDataMap, Map.of(dataType, rawData));
            transformDataTypes.add(dataType);
        }

        File dir = AssayFileWriter.ensureUploadDirectory(context.getContainer());

        assert mergedDataMap.size() <= 1 : "Multiple input files are only supported if they are of the same type";

        for (Map.Entry<DataType, List<Map<String, Object>>> dataEntry : mergedDataMap.entrySet())
        {
            File runData = new File(scriptDir, Props.runDataFile + ".tsv");
            getDataSerializer().exportRunData(context.getProtocol(), dataEntry.getValue(), runData);
            _filesToIgnore.add(runData);

            pw.append(Props.runDataFile.name());
            pw.append('\t');
            pw.append(runData.getAbsolutePath());
            pw.append('\t');
            pw.append(dataEntry.getKey().getNamespacePrefix());

            if (transformDataTypes.contains(dataEntry.getKey()))
            {
                // if the handler supports data transformation, we will include an additional column for the location of
                // a transformed data file that a transform script may create.
                File transformedData = AssayFileWriter.createFile(context.getProtocol(), dir, "tsv");

                pw.append('\t');
                pw.append(transformedData.getAbsolutePath());
            }
            pw.println();
        }

        return new HashSet<>(result);
    }

    protected void addToMergedMap(Map<DataType, List<Map<String, Object>>> mergedDataMap, Map<DataType, List<Map<String, Object>>> dataMap)
    {
        for (Map.Entry<DataType, List<Map<String, Object>>> entry : dataMap.entrySet())
        {
            if (mergedDataMap.containsKey(entry.getKey()))
            {
                mergedDataMap.get(entry.getKey()).addAll(entry.getValue());
            }
            else
            {
                mergedDataMap.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Called to write out uploaded run data that has been previously transformed.
     */
    protected Set<File> _writeTransformedRunData(AssayRunUploadContext context, TransformResult transformResult, ExpRun run, File scriptDir, PrintWriter pw) throws Exception
    {
        assert (!transformResult.getTransformedData().isEmpty());

        Set<File> result = new HashSet<>();

        // the original uploaded data file(s)
        List<File> originalFiles = transformResult.getUploadedFiles();
        if (originalFiles != null && !originalFiles.isEmpty())
        {
            pw.append(Props.runDataUploadedFile.name());
            pw.append('\t');
            String originalFilesString = originalFiles.stream().map(File::getAbsolutePath).collect(Collectors.joining(";"));
            pw.append(originalFilesString);
            pw.println();

            result.addAll(originalFiles);
        }


        AssayFileWriter.ensureUploadDirectory(context.getContainer());
        File workDir = getWorkingDirectory(context);

        for (Map.Entry<ExpData, List<Map<String, Object>>> entry : transformResult.getTransformedData().entrySet())
        {
            ExpData data = entry.getKey();
            File runData = new File(scriptDir, Props.runDataFile + ".tsv");
            // ask the data serializer to write the data map out to the temp file
            getDataSerializer().exportRunData(context.getProtocol(), entry.getValue(), runData);

            pw.append(Props.runDataFile.name());
            pw.append('\t');
            pw.append(data.getFile().getAbsolutePath());
            result.add(data.getFile());
            pw.append('\t');
            pw.append(data.getLSIDNamespacePrefix());

            // Include an additional column for the location of a transformed data file that a transform script may create.
            // Get directory from first input file found
            if (workDir != null)
            {
                File transformedData = AssayFileWriter.createFile(context.getProtocol(), workDir, "tsv");
                pw.append('\t');
                pw.append(transformedData.getAbsolutePath());
            }

            pw.println();
        }
        return result;
    }

    protected void addSampleProperties(String propertyName, List<Map<String, Object>> rows)
    {
        _sampleProperties.put(propertyName, rows);
    }

    protected void writeRunProperties(AssayRunUploadContext<? extends AssayProvider> context, Map<DomainProperty, String> runProperties, File scriptDir, PrintWriter pw, TSVWriter writer)
    {
        // serialize the run properties to a tsv
        for (Map.Entry<DomainProperty, String> entry : runProperties.entrySet())
        {
            pw.append(writer.quoteValue(entry.getKey().getName()));
            pw.append('\t');
            pw.append(writer.quoteValue(StringUtils.defaultString(entry.getValue())));
            pw.append('\t');
            pw.println(writer.quoteValue(entry.getKey().getPropertyDescriptor().getPropertyType().getJavaType().getName()));
        }

        // additional context properties
        for (Map.Entry<String, String> entry : getContextProperties(context, scriptDir).entrySet())
        {
            pw.append(writer.quoteValue(entry.getKey()));
            pw.append('\t');
            pw.append(writer.quoteValue(entry.getValue()));
            pw.append('\t');
            pw.println(writer.quoteValue(String.class.getName()));
        }
    }

    public Map<DomainProperty, String> getRunProperties(AssayRunUploadContext<? extends AssayProvider> context) throws ExperimentException
    {
        Map<DomainProperty, String> runProperties = new HashMap<>(context.getRunProperties());
        for (Map.Entry<DomainProperty, String> entry : runProperties.entrySet())
            _formFields.put(entry.getKey().getName(), entry.getValue());

        runProperties.putAll(context.getBatchProperties());
        return runProperties;
    }

    private Map<String, String> getContextProperties(AssayRunUploadContext context, File scriptDir)
    {
        Map<String, String> map = new HashMap<>();

        map.put(Props.assayId.name(), StringUtils.defaultString(context.getName()));
        map.put(Props.runComments.name(), StringUtils.defaultString(context.getComments()));
        map.put(Props.baseUrl.name(), AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath());
        map.put(Props.containerPath.name(), context.getContainer().getPath());
        map.put(Props.assayType.name(), context.getProvider().getName());
        map.put(Props.assayName.name(), context.getProtocol().getName());
        map.put(Props.userName.name(), StringUtils.defaultString(context.getUser().getEmail()));
        map.put(Props.workingDir.name(), scriptDir.getAbsolutePath());
        map.put(Props.protocolId.name(), String.valueOf(context.getProtocol().getRowId()));
        map.put(Props.protocolDescription.name(), StringUtils.defaultString(context.getProtocol().getDescription()));
        map.put(Props.protocolLsid.name(), context.getProtocol().getLSID());
        File originalFileLocation = context.getOriginalFileLocation();
        if (originalFileLocation != null)
        {
            map.put(Props.originalFileLocation.name(), originalFileLocation.getPath());
        }

        return map;
    }

    public RunInfo processRunInfo(File runInfo) throws ValidationException
    {
        RunInfo info = new RunInfo();
        if (runInfo.exists())
        {

            try (TabLoader loader = new TabLoader(runInfo, false))
            {
                // Don't unescape file path names on windows (C:\foo\bar.tsv)
                loader.setUnescapeBackslashes(false);
                loader.setColumns(new ColumnDescriptor[]{
                        new ColumnDescriptor("name", String.class),
                        new ColumnDescriptor("value", String.class),
                        new ColumnDescriptor("type", String.class)
                });

                for (Map<String, Object> row : loader)
                {
                    if (row.get("name").equals(Props.errorsFile.name()))
                    {
                        info.setErrorFile(new File(row.get("value").toString()));
                    }
                    if (row.get("name").equals(Props.severityLevel.name()))
                    {
                        info.setWarningSevLevel(row.get("value").toString());
                    }
                }
            }
            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }

        }
        return info;
    }

    private void cleanFiles(RunInfo info, DefaultTransformResult result, List<File> files, @Nullable String errorFile, @Nullable File transformedFile)
    {
        // If the assay does not have the "save script files for debugging" option selected, then do file cleanup
        if (!info.isSaveScriptFiles())
        {
            // If file was not already on the server, erase original files from working directory
            if (info.getOriginalFileLocation() == null)
            {
                for (File file : result.getUploadedFiles())
                {
                    FileUtils.deleteQuietly(file);
                }
            }

            // More file cleanup on error
            for (File file : files)
            {
                FileUtils.deleteQuietly(file);
            }

            if (errorFile != null)
            {
                FileUtils.deleteQuietly(new File(errorFile));
            }

            if (transformedFile != null)
            {
                FileUtils.deleteQuietly(transformedFile);
            }
        }
    }

    private void processWarningsOutput(
            DefaultTransformResult result,
            @Nullable Map<String, String> transformedProps,
            RunInfo info,
            @Nullable String errorFile,
            @Nullable File transformedFile,
            List<File> files) throws ValidationException
    {
        String maxSeverity = null;

        if(null != transformedProps)
        {
            for (Map.Entry<String, String> row : transformedProps.entrySet())
            {
                if (row.getKey().equals(Props.maximumSeverity.name()))
                {
                    maxSeverity = row.getValue();
                    break;
                }
            }
        }

        // Look for error file and get contents
        String warning = null;
        if (null != errorFile)
        {
            File errFile = new File(errorFile);
            if (errFile.exists())
            {
                File errors = new File(errorFile);
                try
                {
                    warning = PageFlowUtil.getFileContentsAsString(errors);
                }
                catch (Exception e)
                {
                    warning = null;
                }
            }
        }

        // Display warnings case
        if(null != info.getWarningSevLevel() && info.getWarningSevLevel().equals(errLevel.WARN.name()) && null != maxSeverity && maxSeverity.equals(errLevel.WARN.name()))
        {
            // Running in background does not support warnings
            if(info.isBackgroundUpload())
            {
                cleanFiles(info, result, files, errorFile, transformedFile);
                ValidationException exception = new ValidationException();
                exception.addFieldError("transform", "Background assay import does not support warnings.");
                exception.addFieldError("Warning", warning);
                throw exception;
            }

            if(null != warning)
                result.setWarnings(warning);
            else
                result.setWarnings("Warnings found in transform script!");

            if(null != files && !files.isEmpty())
                result.setFiles(files);
        }
        // if error file exists
        else if(null != warning || (null != maxSeverity && maxSeverity.equals(errLevel.ERROR.name())))
        {
            cleanFiles(info, result, files, errorFile, transformedFile);

            if(null != warning)
            {
                ValidationException exception = new ValidationException();
                exception.addFieldError("transform", warning);
                throw exception;
            }
            // if error indicated in transformPropertiesFile
            else if(null != maxSeverity && maxSeverity.equals(errLevel.ERROR.name()))
            {
                throw new ValidationException("Transform script has thrown errors.");
            }
        }

    }

    public void processValidationOutput(RunInfo info, @Nullable Logger log) throws ValidationException
    {
        List<ValidationError> errors = new ArrayList<>();

        if (info.getErrorFile() != null && info.getErrorFile().exists())
        {
            try (TabLoader errorLoader = new TabLoader(info.getErrorFile(), false))
            {
                errorLoader.setColumns(new ColumnDescriptor[]{
                        new ColumnDescriptor("type", String.class),
                        new ColumnDescriptor("property", String.class),
                        new ColumnDescriptor("message", String.class)
                });

                for (Map<String, Object> row : errorLoader)
                {
                    if ("error".equalsIgnoreCase(row.get("type").toString()))
                    {
                        String propName = mapPropertyName(StringUtils.trimToNull((String) row.get("property")));

                        if (propName != null)
                            errors.add(new PropertyValidationError(row.get("message").toString(), propName));
                        else
                            errors.add(new SimpleValidationError(row.get("message").toString()));
                    }
                    else if ("warn".equalsIgnoreCase(row.get("type").toString()) && log != null)
                    {
                        log.warn(row.get("message").toString());
                    }
                }
            }

            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);

        }
    }

    /**
     * Ensures the property name recorded maps to a valid form field name
     */
    protected String mapPropertyName(String name)
    {
        if (Props.assayId.name().equals(name))
            return "name";
        if (Props.runComments.name().equals(name))
            return "comments";
        if (_formFields.containsKey(name))
            return name;

        return null;
    }

    @Override
    public void createSampleData(@NotNull ExpProtocol protocol, ViewContext viewContext, File scriptDir) throws Exception
    {
        final int SAMPLE_DATA_ROWS = 5;
        File runProps = new File(scriptDir, VALIDATION_RUN_INFO_FILE);

        // Hack to get TSV values to be properly quoted if they include tabs
        TSVWriter writer = createTSVWriter();

        try (PrintWriter pw = PrintWriters.getPrintWriter(runProps))
        {
            AssayRunUploadContext<? extends AssayProvider> context = new SampleRunUploadContext(protocol, viewContext);

            writeRunProperties(context, context.getRunProperties(), scriptDir, pw, writer);

            // create the sample run data
            AssayProvider provider = AssayService.get().getProvider(protocol);
            List<Map<String, Object>> dataRows = new ArrayList<>();

            Domain runDataDomain = provider.getResultsDomain(protocol);
            if (runDataDomain != null)
            {
                List<? extends DomainProperty> properties = runDataDomain.getProperties();
                for (int i = 0; i < SAMPLE_DATA_ROWS; i++)
                {
                    Map<String, Object> row = new HashMap<>();
                    for (DomainProperty prop : properties)
                        row.put(prop.getName(), getSampleValue(prop));

                    dataRows.add(row);
                }
                File runData = new File(scriptDir, RUN_DATA_FILE);
                pw.append(Props.runDataFile.name());
                pw.append('\t');
                pw.println(runData.getAbsolutePath());

                getDataSerializer().exportRunData(protocol, new ArrayList<>(dataRows), runData);
            }

            // any additional sample property sets
            for (Map.Entry<String, List<Map<String, Object>>> set : _sampleProperties.entrySet())
            {
                File sampleData = new File(scriptDir, set.getKey() + ".tsv");
                getDataSerializer().exportRunData(protocol, set.getValue(), sampleData);

                pw.append(set.getKey());
                pw.append('\t');
                pw.println(sampleData.getAbsolutePath());
            }

            // errors file location
            File errorFile = new File(scriptDir, ERRORS_FILE);
            pw.append(Props.errorsFile.name());
            pw.append('\t');
            pw.println(errorFile.getAbsolutePath());
        }
    }

    /** Hack to get output TSV to properly quote values with tabs in them */
    private TSVWriter createTSVWriter()
    {
        return new TSVWriter()
        {
            @Override
            protected void write()
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    protected List<Map<String, Object>> parseRunInfo(File runInfo)
    {
        try (TabLoader loader = new TabLoader(runInfo, false))
        {
            // Don't unescape file path names on windows (C:\foo\bar.tsv)
            loader.setUnescapeBackslashes(false);
            loader.setColumns(new ColumnDescriptor[]{
                    new ColumnDescriptor("name", String.class),
                    new ColumnDescriptor("value", String.class),
                    new ColumnDescriptor("type", String.class),
                    new ColumnDescriptor("transformedData", String.class)
            });
            return loader.load();
        }
    }

    protected boolean isIgnorableOutput(File file)
    {
        return _filesToIgnore.contains(file);
    }

    public static String directoryKey(AssayRunUploadContext context)
    {
        if(context instanceof ProtocolIdForm && null != ((ProtocolIdForm) context).getUploadAttemptID())
            return ((ProtocolIdForm) context).getUploadAttemptID();

        return context.getContainer().getId() + "-" + context.getUser().getUserId() + "-" + context.getName();
    }

    @Nullable
    public static File getWorkingDirectory(AssayRunUploadContext context) {
        Pair<Container, File> containerFilePair = workingDirectories.get(directoryKey(context));
        if(containerFilePair != null
                && containerFilePair.first.hasPermission("TsvDataExchangeHandler.getWorkingDirectory()", context.getUser(), ReadPermission.class))
        {
            return containerFilePair.second;
        }
        return null;
    }

    @Nullable
    public static File getWorkingDirectory(ProtocolIdForm form, User u) {
        Pair<Container, File> containerFilePair = workingDirectories.get(form.getUploadAttemptID());
        if(containerFilePair != null
                && containerFilePair.first.hasPermission("TsvDataExchangeHandler.getWorkingDirectory()", u, ReadPermission.class))
        {
            return containerFilePair.second;
        }
        return null;
    }

    @Nullable
    public static File removeWorkingDirectory(AssayRunUploadContext context) {
        Pair<Container, File> containerFilePair = workingDirectories.get(directoryKey(context));
        if(containerFilePair != null
                && containerFilePair.first.hasPermission("TsvDataExchangeHandler.removeWorkingDirectory()", context.getUser(), ReadPermission.class))
        {
            containerFilePair = workingDirectories.remove(directoryKey(context));
            return containerFilePair.second;
        }
        return null;
    }

    @Nullable
    public static File removeWorkingDirectory(ProtocolIdForm form, User u) {
        Pair<Container, File> containerFilePair = workingDirectories.get(form.getUploadAttemptID());
        if(containerFilePair != null
                && containerFilePair.first.hasPermission("TsvDataExchangeHandler.removeWorkingDirectory()", u, ReadPermission.class))
        {
            containerFilePair = workingDirectories.remove(form.getUploadAttemptID());
            return containerFilePair.second;
        }
        return null;
    }

    public static void setWorkingDirectory(AssayRunUploadContext context, File dir) {
        if(context.getContainer().hasPermission("TsvDataExchangeHandler.setWorkingDirectory()", context.getUser(), ReadPermission.class))
        {
            workingDirectories.put(directoryKey(context), new Pair<>(context.getContainer(), dir));
        }
    }

    private class RunInfo {
        private File _errorFile;
        private String _warningSevLevel;
        private File _originalFileLocation;
        private ExpProtocol _protocol;

        public void setProtocol(ExpProtocol protocol)
        {
            _protocol = protocol;
        }

        public boolean isSaveScriptFiles()
        {
            AssayProvider provider = AssayService.get().getProvider(_protocol);
            return provider.isSaveScriptFiles(_protocol);
        }

        public boolean isBackgroundUpload()
        {
            AssayProvider provider = AssayService.get().getProvider(_protocol);
            return provider.isBackgroundUpload(_protocol);
        }

        // Original file location used to determine if file was already on the server or not. Used for cleanup when
        // there is an error.
        public File getOriginalFileLocation()
        {
            return _originalFileLocation;
        }

        public void setOriginalFileLocation(File originalFileLocation)
        {
            _originalFileLocation = originalFileLocation;
        }

        public File getErrorFile()
        {
            return _errorFile;
        }

        public void setErrorFile(File errorFile)
        {
            _errorFile = errorFile;
        }

        public String getWarningSevLevel()
        {
            return _warningSevLevel;
        }

        public void setWarningSevLevel(String warningSevLevel)
        {
            _warningSevLevel = warningSevLevel;
        }
    }

    @Override
    public TransformResult processTransformationOutput(AssayRunUploadContext<? extends AssayProvider> context, File runInfo, ExpRun run, File scriptFile, TransformResult mergeResult, Set<File> inputDataFiles) throws ValidationException
    {
        Logger log = context.getLogger();
        if (log == null)
            log = LOG;

        DefaultTransformResult result = new DefaultTransformResult(mergeResult);
        _filesToIgnore.add(scriptFile);

        // Get data for processing errors and warnings
        RunInfo info = processRunInfo(runInfo);

        // check to see if any errors were generated
        processValidationOutput(info, context.getLogger());

        // Set original file location for file cleanup if necessary
        info.setOriginalFileLocation(context.getOriginalFileLocation());

        // Set protocol
        info.setProtocol(context.getProtocol());

        // Find the output step for the run
        ExpProtocolApplication outputProtocolApplication = null;
        for (ExpProtocolApplication protocolApplication : run.getProtocolApplications())
        {
            if (protocolApplication.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRunOutput)
            {
                outputProtocolApplication = protocolApplication;
            }
        }

        // Create an extra ProtocolApplication that represents the script invocation
        ExpProtocolApplication scriptPA = ExperimentService.get().createSimpleRunExtraProtocolApplication(run, scriptFile.getName());
        scriptPA.save(context.getUser());

        DataType dataType = context.getProvider().getDataType();
        if (dataType == null)
            dataType = TsvDataHandler.RELATED_TRANSFORM_FILE_DATA_TYPE;

        // Wire up the script's inputs
        Lsid.LsidBuilder builder = new Lsid.LsidBuilder(ExpData.DEFAULT_CPAS_TYPE,"");
        for (File dataFile : inputDataFiles)
        {
            ExpData data = ExperimentService.get().getExpDataByURL(dataFile, context.getContainer());
            if (data == null)
            {
                data = ExperimentService.get().createData(context.getContainer(), dataType, dataFile.getName());
                data.setLSID(builder.setObjectId(GUID.makeGUID()).build());
                data.setDataFileURI(dataFile.toURI());
                data.save(context.getUser());
            }
            scriptPA.addDataInput(context.getUser(), data, "Data");
        }

        // if input data was transformed,
        if (runInfo.exists())
        {
            try
            {
                PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(context.getContainer());

                List<Map<String, Object>> maps = parseRunInfo(runInfo);
                Map<String, File> transformedData = new HashMap<>();
                File transformedRunProps = null;
                File transformedFile = null;
                List<File> runDataUploadedFiles = new ArrayList<>();
                Map<String, String> transformedProps = null;
                String transErrorFile = null;

                for (Map<String, Object> row : maps)
                {
                    Object data = row.get("transformedData");
                    if (data != null)
                    {
                        transformedFile = new File(data.toString());
                        if (transformedFile.exists())
                        {
                            transformedData.put(String.valueOf(row.get("type")), transformedFile);
                            _filesToIgnore.add(transformedFile);
                        }
                    }
                    else if (String.valueOf(row.get("name")).equalsIgnoreCase(Props.transformedRunPropertiesFile.name()))
                    {
                        transformedRunProps = new File(row.get("value").toString());
                    }
                    else if (String.valueOf(row.get("name")).equalsIgnoreCase(Props.runDataUploadedFile.name()))
                    {
                        List<String> filePaths = Arrays.asList(row.get("value").toString().split(";"));
                        runDataUploadedFiles.addAll(filePaths.stream().map(File::new).collect(Collectors.toList()));
                    }
                }

                List<File> tempOutputFiles = new ArrayList<>();

                // Issue 35262: assay with transform script fails when using assay-importRun.api and dataRows
                // The runDataUploadedFile will be null when importing via LABKEY.Assay.importRun() and using dataRows (instead of a file or file(s))
                if (!runDataUploadedFiles.isEmpty())
                {
                    tempOutputFiles.addAll(runDataUploadedFiles);
                    // Since there could be many input files, we'll use the parent directory of the first file we found as the working directory
                    setWorkingDirectory(context, new File(runDataUploadedFiles.get(0).getParentFile().getAbsolutePath()));
                }

                // Loop through all of the files that are left after running the transform script
                for (File file : runInfo.getParentFile().listFiles())
                {
                    if (!isIgnorableOutput(file) && !runDataUploadedFiles.isEmpty())
                    {
                        // Since there could be many uploaded data files, we'll just use the base name of the first one we found
                        File firstFile = runDataUploadedFiles.get(0);
                        int extensionIndex = firstFile.getName().lastIndexOf(".");
                        String baseName = extensionIndex >= 0 ? firstFile.getName().substring(0, extensionIndex) : firstFile.getName();

                        // Figure out a unique file name
                        File targetFile;
                        int index = 0;
                        do
                        {
                            targetFile = new File(firstFile.getParentFile(), baseName + (index == 0 ? "" : ("-" + index)) + "." + file.getName());
                            index++;
                        }
                        while (targetFile.exists());

                        // Catch errors file
                        if(file.getName().equals(TRANS_ERR_FILE))
                        {
                            transErrorFile = targetFile.getPath();
                        }
                        else
                            tempOutputFiles.add(targetFile);


                        // Copy the file to the same directory as the original data file
                        FileUtils.moveFile(file, targetFile);

                        // Add the file as an output to the run, and as being created by the script
                        Pair<ExpData,String> outputData = DefaultAssayRunCreator.createdRelatedOutputData(context, baseName, targetFile);
                        if (outputData != null)
                        {
                            outputData.getKey().setSourceApplication(scriptPA);
                            outputData.getKey().save(context.getUser());

                            outputProtocolApplication.addDataInput(context.getUser(), outputData.getKey(), outputData.getValue());
                        }
                    }
                }

                if (!transformedData.isEmpty())
                {
                    // found some transformed data, create the ExpData objects and return in the transform result
                    Map<ExpData, List<Map<String, Object>>> dataMap = new HashMap<>();

                    for (Map.Entry<String, File> entry : transformedData.entrySet())
                    {
                        File file = entry.getValue();
                        String type = entry.getKey();
                        log.debug("processing transformed data file: type=" + type + ", file=" + file.getPath());

                        File workingDir = getWorkingDirectory(context);
                        if (workingDir == null)
                        {
                            // If we don't have runDataUploadedFile, check the transformed data path is under the pipeline root.
                            // If the transformed data is under the pipeline root, don't move it and use it as is.
                            if (pipeRoot == null || !pipeRoot.isUnderRoot(file))
                                throw new ValidationException("Working directory not found. Either runDataUploadedFile property from runProperties.tsv must be present or the transformed data path must be under the pipeline root directory.");
                        }
                        else
                        {
                            // Move to the working directory
                            File tempDirCopy = new File(workingDir, file.getName());
                            if (!file.equals(tempDirCopy))
                            {
                                log.debug("moving to working directory=" + tempDirCopy);
                                FileUtils.moveFile(file, tempDirCopy);
                                file = tempDirCopy;
                            }
                        }

                        // Add it as an artifact the user can download
                        tempOutputFiles.add(file);

                        ExpData data = ExperimentService.get().getExpDataByURL(file, context.getContainer());
                        if (data == null)
                        {
                            log.debug("exp.data doesn't exist, creating new one");
                            data = DefaultAssayRunCreator.createData(context.getContainer(), file, "transformed output", new DataType(type), true, true, context.getLogger());
                            data.setName(file.getName());
                        }

                        dataMap.put(data, getDataSerializer().importRunData(context.getProtocol(), file));

                        data.setSourceApplication(scriptPA);
                        data.save(context.getUser());
                    }
                    result = new DefaultTransformResult(dataMap);
                    result.setBatchProperties(mergeResult.getBatchProperties());
                    result.setRunProperties(mergeResult.getRunProperties());
                }

                if (transformedRunProps != null && transformedRunProps.exists())
                {
                    transformedProps = new HashMap<>();
                    for (Map<String, Object> row : parseRunInfo(transformedRunProps))
                    {
                        String name = row.get("name") == null ? null : String.valueOf(row.get("name"));
                        String value = row.get("value") == null ? null : String.valueOf(row.get("value"));

                        if (name != null && value != null)
                            transformedProps.put(name, value);
                    }

                    // merge the transformed props with the props in the upload form
                    Map<DomainProperty, String> runProps = new HashMap<>();
                    boolean runPropTransformed = false;
                    Map<DomainProperty, String> runProperties = result.getRunProperties().size() > 0 ? result.getRunProperties() : getRunProperties(context);
                    for (Map.Entry<DomainProperty, String> entry : runProperties.entrySet())
                    {
                        String propName = entry.getKey().getName();
                        if (transformedProps.containsKey(propName))
                        {
                            runProps.put(entry.getKey(), transformedProps.get(propName));
                            runPropTransformed = true;
                        }
                        else
                            runProps.put(entry.getKey(), entry.getValue());
                    }

                    Map<DomainProperty, String> batchProps = new HashMap<>();
                    boolean batchPropTransformed = false;
                    Map<DomainProperty, String> batchProperties = result.getBatchProperties().size() > 0 ? result.getBatchProperties() : context.getBatchProperties();
                    for (Map.Entry<DomainProperty, String> entry : batchProperties.entrySet())
                    {
                        String propName = entry.getKey().getName();
                        if (transformedProps.containsKey(propName))
                        {
                            batchProps.put(entry.getKey(), transformedProps.get(propName));
                            batchPropTransformed = true;
                        }
                        else
                            batchProps.put(entry.getKey(), entry.getValue());
                    }

                    if (runPropTransformed)
                        result.setRunProperties(runProps);
                    if (batchPropTransformed)
                        result.setBatchProperties(batchProps);
                    if (transformedProps.containsKey(Props.assayId.name()))
                        result.setAssayId(transformedProps.get(Props.assayId.name()));
                    if (transformedProps.containsKey(Props.runComments.name()))
                        result.setComments(transformedProps.get(Props.runComments.name()));
                }
                if (!runDataUploadedFiles.isEmpty())
                    result.setUploadedFiles(runDataUploadedFiles);

                // Don't offer up input or other files as "outputs" of the script
                tempOutputFiles.removeAll(_filesToIgnore);

                processWarningsOutput(result, transformedProps, info, transErrorFile, transformedFile, tempOutputFiles);
            }
            catch (ValidationException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }
        }
        return result;
    }

    protected static String getSampleValue(DomainProperty prop)
    {
        switch (prop.getPropertyDescriptor().getPropertyType().getJdbcType())
        {
            case BOOLEAN :
                return "true";
            case TIMESTAMP:
                DateFormat format = new SimpleDateFormat("MM/dd/yyyy");
                return format.format(new Date());
            case DOUBLE:
            case INTEGER:
                return "1234";
            default:
                return "demo value";
        }
    }

    private static class SampleRunUploadContext implements AssayRunUploadContext<AssayProvider>
    {
        ExpProtocol _protocol;
        ViewContext _context;

        public SampleRunUploadContext(@NotNull ExpProtocol protocol, ViewContext context)
        {
            _protocol = protocol;
            _context = context;
        }

        @Override
        @NotNull
        public ExpProtocol getProtocol()
        {
            return _protocol;
        }

        @Override
        public Map<DomainProperty, String> getRunProperties()
        {
            AssayProvider provider = AssayService.get().getProvider(_protocol);
            return createDomainProperties(provider != null ? provider.getRunDomain(_protocol) : null);
        }

        @Override
        public Map<DomainProperty, String> getBatchProperties()
        {
            AssayProvider provider = AssayService.get().getProvider(_protocol);
            return createDomainProperties(provider != null ? provider.getBatchDomain(_protocol) : null);
        }

        private Map<DomainProperty, String> createDomainProperties(Domain d)
        {
            if (d == null)
                return emptyMap();

            Map<DomainProperty, String> properties = new HashMap<>();
            for (DomainProperty prop : d.getProperties())
                properties.put(prop, getSampleValue(prop));

            return unmodifiableMap(properties);
        }

        @Override
        public String getComments()
        {
            return "sample upload comments";
        }

        @Override
        public String getName()
        {
            return "sample upload name";
        }

        @Override
        public Integer getWorkflowTask()
        {
            return null;
        }

        @Override
        public User getUser()
        {
            return _context.getUser();
        }

        public String getSeverityLevel() { return null;}

        public void setSeverityLevel(String severityLevel) {};

        @Override
        @NotNull
        public Container getContainer()
        {
            return _context.getContainer();
        }

        @Override
        public HttpServletRequest getRequest()
        {
            return _context.getRequest();
        }

        @Override
        public ActionURL getActionURL()
        {
            return _context.getActionURL();
        }

        @Override
        @NotNull
        public Map<String, File> getUploadedData()
        {
            return emptyMap();
        }

        @NotNull
        @Override
        public Map<Object, String> getInputDatas()
        {
            return emptyMap();
        }

        @Override
        public AssayProvider getProvider()
        {
            return AssayService.get().getProvider(_protocol);
        }

        @Override
        public Integer getReRunId()
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        @Override
        public String getTargetStudy()
        {
            return null;
        }

        @Override
        public TransformResult getTransformResult()
        {
            return null;
        }

        @Override
        public void setTransformResult(TransformResult result)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void uploadComplete(ExpRun run)
        {
            // no-op
        }

        @Override
        public Logger getLogger()
        {
            return null;
        }

        @Nullable
        @Override
        public File getOriginalFileLocation()
        {
            return null;
        }
    }
}
