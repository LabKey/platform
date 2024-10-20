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

package org.labkey.api.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.Readers;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityManager.TransformSession;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.labkey.api.util.UnexpectedException;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Runs the selected data file(s) through the assay design's transform scripts. Writes the data to a standard TSV
 * format when possible to make it easier for the scripts to consume. Loads the script's output into a TransformResult
 * so it can be consumed/imported by other code.
 *
 * User: klum
 * Date: Sep 22, 2009
 */
public class DefaultDataTransformer<ProviderType extends AssayProvider> implements DataTransformer<ProviderType>
{
    public static final String RUN_INFO_REPLACEMENT = "runInfo";
    public static final String SRC_DIR_REPLACEMENT = "srcDirectory";
    public static final String R_SESSIONID_REPLACEMENT = "rLabkeySessionId";
    public static final String LEGACY_SESSION_ID_REPLACEMENT = "httpSessionId";
    public static final String LEGACY_SESSION_COOKIE_NAME_REPLACEMENT = "sessionCookieName";
    public static final String BASE_SERVER_URL_REPLACEMENT = "baseServerURL";
    public static final String CONTAINER_PATH = "containerPath";
    public static final String ORIGINAL_SOURCE_PATH = "OriginalSourcePath";

    @Override
    public TransformResult transformAndValidate(AssayRunUploadContext<ProviderType> context, ExpRun run) throws ValidationException
    {
        boolean isDefault = isDefault(context.getProtocol());
        TransformResult result = DefaultTransformResult.createEmptyResult();
        DataExchangeHandler dataHandler = context.getProvider().createDataExchangeHandler();
        if (dataHandler == null)
        {
            return result;
        }

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
        
        for (File scriptFile : context.getProvider().getValidationAndAnalysisScripts(context.getProtocol(), AssayProvider.Scope.ALL))
        {
            // read the contents of the script file
            if (scriptFile.exists())
            {
                // TODO: don't bother slurping the contents of binary scripts
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = Readers.getReader(scriptFile))
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
                String ext = FileUtil.getExtension(scriptFile);
                if (ext != null)
                {
                    engine = LabKeyScriptEngineManager.get()
                        .getEngineByExtension(context.getContainer(), ext, LabKeyScriptEngineManager.EngineContext.pipeline);
                }
                if (engine != null)
                {
                    // issue : 46838 remote scripting engines don't support transform scripts yet
                    if (engine instanceof ExternalScriptEngine externalScriptEngine)
                    {
                        if (!externalScriptEngine.supportsContext(LabKeyScriptEngineManager.EngineContext.pipeline))
                            throw new ValidationException("The script engine : " + externalScriptEngine.getEngineDefinition().getName() + " does not support running in a transform script." );
                    }
                    FileLike scriptDir = null;
                    // issue 19748: need alternative to JSESSIONID for pipeline job transform script usage (i.e., TransformSession)
                    try (TransformSession session = SecurityManager.createTransformSession(context))
                    {
                        scriptDir = getScriptDir(context.getProtocol(), scriptFile, isDefault);
                        // issue 13643: ensure script dir is initially empty
                        FileUtil.deleteDirectoryContents(scriptDir);

                        Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                        String script = sb.toString();
                        Pair<FileLike, Set<FileLike>> files = dataHandler.createTransformationRunInfo(context, run, scriptDir, runProperties, batchProperties);
                        FileLike runInfo = files.getKey();

                        bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, scriptDir.toNioPathForWrite().toString());
                        bindings.put(ExternalScriptEngine.SCRIPT_PATH, scriptFile.getAbsolutePath());

                        Map<String, String> paramMap = new HashMap<>();

                        paramMap.put(RUN_INFO_REPLACEMENT, runInfo.toNioPathForWrite().toFile().getAbsolutePath());

                        addStandardParameters(context.getRequest(), context.getContainer(), scriptFile, session.getApiKey(), paramMap);

                        bindings.put(ExternalScriptEngine.PARAM_REPLACEMENT_MAP, paramMap);

                        Object output = engine.eval(script);

                        FileLike rewrittenScriptFile = null;
                        if (bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE) instanceof File)
                        {
                            var rewrittenScriptFileObject = bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE);
                            if (rewrittenScriptFileObject instanceof FileLike fo)
                                rewrittenScriptFile = fo;
                            else
                                rewrittenScriptFile = FileSystemLike.wrapFile((File)rewrittenScriptFileObject);
                        }
                        else
                        {
                            rewrittenScriptFile = FileSystemLike.wrapFile(scriptFile);
                        }

                        // process any output from the transformation script
                        result = dataHandler.processTransformationOutput(context, runInfo, run, rewrittenScriptFile, result, files.getValue());

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
                    catch (ValidationException e)
                    {
                        throw e;
                    }
                    catch (Exception e)
                    {
                        ValidationException ve = new ValidationException(e.getMessage() == null ? e.toString() : e.getMessage());
                        ve.initCause(e);
                        throw ve;
                    }
                    finally
                    {
                        // clean up temp directory
                        if (!isDefault)
                        {
                            try
                            {
                                if (null != scriptDir)
                                {
                                    FileUtil.deleteDir(scriptDir.toNioPathForWrite().toFile());
                                    FileLike parent = scriptDir.getParent();
                                    if (parent != null)
                                        parent.delete();
                                }
                            }
                            catch (IOException e)
                            {
                                throw UnexpectedException.wrap(e);
                            }
                        }
                    }
                }
                else
                {
                    // we may just want to log an error rather than fail the upload due to an engine config problem.
                    throw new ValidationException("A script engine implementation was not found for the specified QC script (" + scriptFile.getName() + "). " +
                            "Check configurations in the Admin Console.");
                }
            }
            else
            {
                throw new ValidationException("The transform script, " + scriptFile.getAbsolutePath() + ", configured for this assay does not exist. Please check " +
                        "the configuration for this assay design.");
            }
        }
        return result;
    }

    public static void addStandardParameters(@Nullable HttpServletRequest request, @Nullable Container container, @Nullable File scriptFile, @Nullable String apiKey, @NotNull Map<String, String> paramMap)
    {
        if (scriptFile != null)
        {
            File srcDir = scriptFile.getParentFile();
            if (srcDir != null && srcDir.exists())
                paramMap.put(SRC_DIR_REPLACEMENT, srcDir.getAbsolutePath().replaceAll("\\\\", "/"));
        }
        paramMap.put(R_SESSIONID_REPLACEMENT, getSessionInfo(request, apiKey));
        paramMap.put(LEGACY_SESSION_COOKIE_NAME_REPLACEMENT, getSessionCookieName(request));
        paramMap.put(LEGACY_SESSION_ID_REPLACEMENT, getSessionId(request, apiKey));
        paramMap.put(SecurityManager.API_KEY, apiKey);
        paramMap.put(BASE_SERVER_URL_REPLACEMENT, AppProps.getInstance().getBaseServerUrl()
                                                    + AppProps.getInstance().getContextPath());
        paramMap.put(CONTAINER_PATH, container == null ? null : container.getPath());
    }

    @Deprecated
    private static String getSessionCookieName(@Nullable HttpServletRequest request)
    {
        if (request != null)
        {
            return CSRFUtil.SESSION_COOKIE_NAME;
        }
        // issue 19748: need alternative to JSESSIONID for pipeline job transform script usage
        return SecurityManager.TRANSFORM_SESSION_ID;
    }

    protected FileLike getScriptDir(ExpProtocol protocol, File scriptFile, boolean isDefault) throws IOException
    {
        FileLike tempDir = FileUtil.getTempDirectoryFileLike();
        FileLike tempRoot = tempDir.resolveChild(ExternalScriptEngine.DEFAULT_WORKING_DIRECTORY);

        if (isDefault && scriptFile.exists())
        {
            // TODO getScriptDir(FileLike scriptFile);
            tempDir = new FileSystemLike.Builder(scriptFile.getParentFile()).readwrite().root();
            tempRoot = tempDir.resolveChild("TransformAndValidationFiles");
        }

        if (!tempRoot.exists())
            FileUtil.mkdir(tempRoot);

        FileLike tempParent = tempRoot.resolveChild("AssayId_" + protocol.getRowId());
        FileLike tempFolder = AssayFileWriter.findUniqueFileName("work", tempParent);
        if (!tempFolder.exists())
            FileUtil.mkdirs(tempFolder);

        return tempFolder;
    }

    protected boolean isDefault(ExpProtocol protocol)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider != null)
            return provider.isSaveScriptFiles(protocol);
        return false;
    }

    /**
     * Creates the session information string
     */
    private static String getSessionInfo(@Nullable HttpServletRequest request, String apiKey)
    {
        return "labkey.sessionCookieName = \"" + getSessionCookieName(request) + "\"\n" +
               "labkey.sessionCookieContents = \"" + getSessionId(request, apiKey) + "\"\n";
    }

    @Deprecated
    private static String getSessionId(@Nullable HttpServletRequest request, String apiKey)
    {
        if (request != null)
        {
            return PageFlowUtil.getCookieValue(request.getCookies(), CSRFUtil.SESSION_COOKIE_NAME, "");
        }
        return apiKey;
    }
}
