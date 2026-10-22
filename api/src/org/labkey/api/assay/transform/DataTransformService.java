/*
 * Copyright (c) 2009-2026 LabKey Corporation
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
package org.labkey.api.assay.transform;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.Readers;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.labkey.api.security.SecurityManager.FEATUREFLAG_ALLOW_TRANSFORM_SESSION_ID;

public class DataTransformService
{
    private static final DataTransformService _instance = new DataTransformService();
    private static final Logger LOG = LogHelper.getLogger(DataTransformService.class, "Assay transform scripts");

    private final Set<Long> _protocolsLoggedInTransaction = ConcurrentHashMap.newKeySet();

    private DataTransformService() {}

    public static DataTransformService get()
    {
        return _instance;
    }

    public static final String RUN_INFO_REPLACEMENT = "runInfo";
    public static final String SRC_DIR_REPLACEMENT = "srcDirectory";
    public static final String R_SESSIONID_REPLACEMENT = "rLabkeySessionId";
    public static final String LEGACY_SESSION_ID_REPLACEMENT = "httpSessionId";
    public static final String LEGACY_SESSION_COOKIE_NAME_REPLACEMENT = "sessionCookieName";
    public static final String BASE_SERVER_URL_REPLACEMENT = "baseServerURL";
    public static final String CONTAINER_PATH = "containerPath";
    public static final String ORIGINAL_SOURCE_PATH = "OriginalSourcePath";
    public static final String TRANSFORM_OPERATION = "transformOperation";

    public enum TransformOperation
    {
        INSERT,
        UPDATE
    }

    /** Runs the transform scripts and records their outputs against run in one step. */
    public TransformResult transformAndValidate(
            AssayRunUploadContext<?> context,
            @Nullable ExpRun run,
            TransformOperation operation
    ) throws ValidationException
    {
        try (PreparedTransform prepared = executeTransform(context, run, null, operation))
        {
            return recordTransform(context, run, prepared);
        }
    }

    /**
     * Runs the assay's transform and validation scripts and reads their output without writing to the database.
     * @param run the saved run, or null when the scripts run before the run is saved
     * @param runDataInputs the run's data inputs when run is null
     */
    public @NotNull PreparedTransform executeTransform(
            AssayRunUploadContext<?> context,
            @Nullable ExpRun run,
            @Nullable Collection<? extends ExpData> runDataInputs,
            TransformOperation operation
    ) throws ValidationException
    {
        DataExchangeHandler dataHandler = context.getProvider().createDataExchangeHandler();
        if (dataHandler == null)
        {
            return PreparedTransform.empty();
        }
        if (runDataInputs != null)
            dataHandler.setRunDataInputs(runDataInputs);

        boolean isDefault = isDefault(context.getProtocol());
        TransformResult result = DefaultTransformResult.createEmptyResult();
        List<TransformScriptOutput> scriptOutputs = new ArrayList<>();
        List<FileLike> scriptDirs = new ArrayList<>();

        Map<DomainProperty, String> batchProperties;
        Map<DomainProperty, String> runProperties;
        try
        {
            batchProperties = context.getBatchProperties();
            runProperties = context.getRunProperties();
        }
        catch (ExperimentException e)
        {
            throw new ValidationException(e.getMessage() == null ? e.toString() : e.getMessage());
        }

        boolean success = false;
        try
        {
            for (AnalysisScript analysisScript : context.getProvider().getValidationAndAnalysisScripts(context.getProtocol(), AssayProvider.Scope.ALL))
            {
                if (!analysisScript.canExecute(operation))
                    continue;

                // read the contents of the script file
                FileLike scriptFile = analysisScript.getScript();
                if (!scriptFile.exists())
                {
                    throw new ValidationException("The transform script, " + scriptFile + ", configured for this assay does not exist. Please check " +
                            "the configuration for this assay design.");
                }

                logIfInTransaction(context, operation);

                if (operation == TransformOperation.UPDATE)
                    SimpleMetricsService.get().increment("Assay", "transformScript", "executionCountForUpdate");
                if (operation == TransformOperation.INSERT)
                    SimpleMetricsService.get().increment("Assay", "transformScript", "executionCountForInsert");

                // TODO: don't bother slurping the contents of binary scripts
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = Readers.getReader(scriptFile.openInputStream()))
                {
                    String l;
                    while ((l = br.readLine()) != null)
                        sb.append(l).append('\n');
                }
                catch (Exception e)
                {
                    throw new ValidationException(e.getMessage());
                }
                ScriptEngine engine = null;
                String ext = FileUtil.getExtension(scriptFile.getName());
                if (ext != null)
                {
                    engine = LabKeyScriptEngineManager.get()
                            .getEngineByExtension(context.getContainer(), ext, LabKeyScriptEngineManager.EngineContext.pipeline);
                }
                if (engine == null)
                {
                    // we may just want to log an error rather than fail the upload due to an engine config problem.
                    throw new ValidationException("A script engine implementation was not found for the specified QC script (" + scriptFile.getName() + "). " +
                            "Check configurations in the Admin Console.");
                }

                // issue : 46838 remote scripting engines don't support transform scripts yet
                if (engine instanceof ExternalScriptEngine externalScriptEngine)
                {
                    if (!externalScriptEngine.supportsContext(LabKeyScriptEngineManager.EngineContext.pipeline))
                        throw new ValidationException("The script engine : " + externalScriptEngine.getEngineDefinition().getName() + " does not support running in a transform script." );
                }
                // issue 19748: need alternative to JSESSIONID for pipeline job transform script usage (i.e., TransformSession)
                try (SecurityManager.TransformSession session = SecurityManager.createTransformSession(context))
                {
                    FileLike scriptDir = getScriptDir(context.getProtocol(), scriptFile, isDefault);
                    if (!isDefault)
                        scriptDirs.add(scriptDir);
                    // issue 13643: ensure script dir is initially empty
                    FileUtil.deleteDirectoryContents(scriptDir);

                    Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                    String script = sb.toString();
                    Pair<FileLike, Set<FileLike>> files = dataHandler.createTransformationRunInfo(operation, context, run, scriptDir, runProperties, batchProperties);
                    FileLike runInfo = files.getKey();

                    bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, scriptDir.toNioPathForWrite().toString());
                    bindings.put(ExternalScriptEngine.SCRIPT_PATH, scriptFile.toNioPathForRead().toFile().getAbsolutePath());
                    bindings.put(ExternalScriptEngine.INVOCATION_LABEL, "transform protocol=" + context.getProtocol().getRowId()
                            + " name='" + context.getProtocol().getName() + "'"
                            + " operation=" + operation.name()
                            + " script='" + scriptFile.getName() + "'"
                            + " container=" + context.getContainer().getPath());

                    int timeoutMinutes = AppProps.getInstance().getAssayTransformScriptTimeout();
                    if (timeoutMinutes > 0)
                        bindings.put(ExternalScriptEngine.TIMEOUT, TimeUnit.MINUTES.toSeconds(timeoutMinutes));

                    Map<String, String> paramMap = new HashMap<>();

                    // Issue 51543: Resolve windows path to run properties
                    paramMap.put(RUN_INFO_REPLACEMENT, runInfo.toNioPathForWrite().toFile().getAbsolutePath().replaceAll("\\\\", "/"));
                    paramMap.put(TRANSFORM_OPERATION, operation.name());

                    addStandardParameters(context.getRequest(), context.getContainer(), scriptFile, session.getApiKey(), paramMap);

                    bindings.put(ExternalScriptEngine.PARAM_REPLACEMENT_MAP, paramMap);
                    bindings.put(ExternalScriptEngine.FAIL_ON_UNREPLACED_PARAMS, true);

                    engine.eval(script);

                    FileLike rewrittenScriptFile;
                    if (bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE) instanceof FileLike file)
                    {
                        rewrittenScriptFile = file;
                    }
                    else
                    {
                        rewrittenScriptFile = scriptFile;
                    }

                    // process any output from the transformation script
                    TransformScriptOutput scriptOutput = new TransformScriptOutput(rewrittenScriptFile.getName(), files.getValue());
                    result = dataHandler.parseTransformationOutput(operation, context, runInfo, rewrittenScriptFile, result, scriptOutput);
                    scriptOutputs.add(scriptOutput);

                    // Propagate any transformed batch properties on to the next script
                    if (result.getBatchProperties() != null && !result.getBatchProperties().isEmpty())
                    {
                        batchProperties = result.getBatchProperties();
                    }
                    // Propagate any transformed run properties on to the next script
                    if (result.getRunProperties() != null && !result.getRunProperties().isEmpty())
                    {
                        runProperties = result.getRunProperties();
                    }

                    context.setTransformResult(result);
                }
                catch (ValidationException | RuntimeException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    ValidationException ve = new ValidationException(e.getMessage() == null ? e.toString() : e.getMessage());
                    ve.initCause(e);
                    throw ve;
                }
            }
            success = true;
            return new PreparedTransform(dataHandler, result, scriptOutputs, scriptDirs);
        }
        finally
        {
            if (!success)
                PreparedTransform.deleteScriptDirs(scriptDirs);
        }
    }

    /** Saves the protocol applications and output data of transform scripts run by {@link #executeTransform}. */
    public TransformResult recordTransform(AssayRunUploadContext<?> context, @Nullable ExpRun run, @NotNull PreparedTransform prepared) throws ValidationException
    {
        if (prepared.isRecorded())
            throw new IllegalStateException("Transform outputs have already been recorded");
        prepared.setRecorded();

        DataExchangeHandler dataHandler = prepared.getHandler();
        if (dataHandler != null)
        {
            for (TransformScriptOutput scriptOutput : prepared.getScriptOutputs())
                dataHandler.recordTransformationOutput(context, run, scriptOutput);
        }
        return prepared.getResult();
    }

    private void logIfInTransaction(AssayRunUploadContext<?> context, TransformOperation operation)
    {
        if (operation == TransformOperation.INSERT
                && context.getProvider().isTransformBeforeRunSupported(context.getProtocol())
                && ExperimentService.get().getSchema().getScope().isTransactionActive()
                && _protocolsLoggedInTransaction.add(context.getProtocol().getRowId()))
        {
            LOG.warn("Transform script for assay '{}' is running inside a database transaction; callbacks from the script into the server can deadlock. Logged once per assay.", context.getProtocol().getName(), new Throwable());
        }
    }

    public void addStandardParameters(@Nullable HttpServletRequest request, @Nullable Container container, @Nullable FileLike scriptFile, @Nullable String apiKey, @NotNull Map<String, String> paramMap)
    {
        if (scriptFile != null)
        {
            FileLike srcDir = scriptFile.getParent();
            if (srcDir != null && srcDir.exists())
                paramMap.put(SRC_DIR_REPLACEMENT, srcDir.toNioPathForRead().toFile().getAbsolutePath().replaceAll("\\\\", "/"));
        }
        if (AppProps.getInstance().isOptionalFeatureEnabled(FEATUREFLAG_ALLOW_TRANSFORM_SESSION_ID))
        {
            paramMap.put(R_SESSIONID_REPLACEMENT, getSessionInfo(request, apiKey));
            paramMap.put(LEGACY_SESSION_COOKIE_NAME_REPLACEMENT, getSessionCookieName(request));
            paramMap.put(LEGACY_SESSION_ID_REPLACEMENT, getSessionId(request, apiKey));
        }
        paramMap.put(SecurityManager.API_KEY, apiKey);
        paramMap.put(BASE_SERVER_URL_REPLACEMENT, AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath());
        paramMap.put(CONTAINER_PATH, container == null ? null : container.getPath());
    }

    @Deprecated
    private String getSessionCookieName(@Nullable HttpServletRequest request)
    {
        if (request != null)
        {
            return SecurityManager.JSESSIONID;
        }
        // issue 19748: need alternative to JSESSIONID for pipeline job transform script usage
        return SecurityManager.TRANSFORM_SESSION_ID;
    }

    private FileLike getScriptDir(ExpProtocol protocol, FileLike scriptFile, boolean isDefault) throws IOException
    {
        FileLike tempDir = FileUtil.getTempDirectoryFileLike();
        FileLike tempRoot = tempDir.resolveChild(ExternalScriptEngine.DEFAULT_WORKING_DIRECTORY);

        if (isDefault && scriptFile.exists())
        {
            tempDir = scriptFile.getParent();
            tempRoot = tempDir.resolveChild("TransformAndValidationFiles");
        }

        FileLike tempParent = tempRoot.resolveChild("AssayId_" + protocol.getRowId());

        // Concurrent imports of the same assay run scripts in parallel, so claim the folder atomically
        int uniquifier = 0;
        while (true)
        {
            FileLike tempFolder = tempParent.resolveChild(FileUtil.getAppendedFileName("work", uniquifier));
            try
            {
                //noinspection SSBasedInspection
                Files.createDirectory(tempFolder.toNioPathForWrite());
                tempFolder.refresh();
                return tempFolder;
            }
            catch (FileAlreadyExistsException e)
            {
                uniquifier++;
            }
            catch (NoSuchFileException e)
            {
                // Missing, or just removed by another import's cleanup of its own folder
                FileUtil.createDirectories(tempParent);
            }
        }
    }

    private boolean isDefault(ExpProtocol protocol)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider != null)
            return provider.isSaveScriptFiles(protocol);
        return false;
    }

    /**
     * Creates the session information string
     */
    private String getSessionInfo(@Nullable HttpServletRequest request, String apiKey)
    {
        if (request == null)
        {
            // GH Issue 1489: background/pipeline jobs have no live HTTP session, so use apikey authentication
            // directly instead of the deprecated LabKeyTransformSessionId cookie.
            return "labkey.setDefaults(apiKey = \"" + apiKey + "\")\n";
        }
        return "labkey.sessionCookieName = \"" + getSessionCookieName(request) + "\"\n" +
                "labkey.sessionCookieContents = \"" + getSessionId(request, apiKey) + "\"\n";
    }

    @Deprecated
    private String getSessionId(@Nullable HttpServletRequest request, String apiKey)
    {
        if (request != null)
        {
            return PageFlowUtil.getCookieValue(request.getCookies(), SecurityManager.JSESSIONID, "");
        }
        return apiKey;
    }
}
