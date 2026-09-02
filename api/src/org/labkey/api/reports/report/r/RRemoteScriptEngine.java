/*
 * Copyright (c) 2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.reports.report.r;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.query.ValidationException;
import org.labkey.api.remoterunner.RemoteRunnerService;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.vfs.FileLike;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

/**
 * Runs R in a remote container reached over HTTP, with the working directory staged through object storage.
 *
 * Shaped after {@link RDockerScriptEngine}: the engine only maps paths and delegates, the transport lives in the
 * injected service.
 */
public class RRemoteScriptEngine extends RScriptEngine
{
    /** Fixed because the runner unpacks the working directory to the same place every run. */
    public static final String REMOTE_WORKING_DIR = "/work";

    private final RemoteRunnerService _service;

    public RRemoteScriptEngine(@NotNull ExternalScriptEngineDefinition def, @Nullable RemoteRunnerService service)
    {
        super(def);
        _service = service;

        def.setPathMapper(new PathMapperImpl()
        {
            void setMapping()
            {
                String wd = getWorkingDir(getContext()).toNioPathForRead().toFile().getAbsolutePath()
                        .replace("\\", "/").replace("/./", "/");
                super.setPathMap(Collections.singletonMap(
                        REMOTE_WORKING_DIR,
                        new File(wd).toURI().toString()));
            }

            @Override
            public String remoteToLocal(String remoteURI)
            {
                setMapping();
                return super.remoteToLocal(remoteURI);
            }

            @Override
            public String localToRemote(String localURI)
            {
                setMapping();
                return super.localToRemote(localURI);
            }

            @Override
            public ValidationException getValidationErrors()
            {
                setMapping();
                return super.getValidationErrors();
            }
        });
    }

    @Override
    protected Object eval(FileLike scriptFile, ScriptContext context) throws ScriptException
    {
        if (null == _service)
            throw new ScriptException("Script evaluation attempted with no RemoteRunnerService instance available.");

        StringBuffer output = new StringBuffer();
        try (CustomTiming t = MiniProfiler.custom("remoteRunner", "execute r in remote runner"))
        {
            _service.executeR(scriptFile, getRWorkingDir(context), REMOTE_WORKING_DIR, inputFiles());
            appendConsoleOutput(context, output);
        }
        catch (Exception e)
        {
            throw new ScriptException("An error occurred when running the script '" + scriptFile.getName()
                    + "', msg " + e.getMessage() + ").\n" + e);
        }

        String scriptOut = output.toString();
        // R CMD BATCH writes to .Rout rather than failing the process, so the only error signal is in the output.
        if (scriptOut.contains("Execution halted"))
            throw new ScriptException("An error occurred when running the script '" + scriptFile.getName() + "'.\n" + scriptOut);
        return scriptOut;
    }

    /**
     * The base class names this file by the appserver's working directory, which does not exist in the container, so
     * the write silently fails and no packages are ever recorded. The runner runs the script from the unpacked job
     * directory, so a bare name lands beside the script and returns in the result tar.
     */
    @Override
    protected @Nullable String getPackageCaptureEpilog(ScriptContext context)
    {
        if (getKnitrFormat(context) != RReportDescriptor.KnitrFormat.None)
            return null;

        return """
                # --- LabKey R package usage capture ---
                tryCatch(writeLines(sort(loadedNamespaces()), "%s"), error = function(e) invisible(NULL))
                """.formatted(PACKAGES_FILE);
    }

    private static FileFilter inputFiles()
    {
        return pathname ->
                pathname.isFile() &&
                        (RScriptEngineFactory.isRScriptEngine(new String[]{FilenameUtils.getExtension(pathname.getName())})
                                || RReport.DATA_INPUT.equals(pathname.getName()));
    }

    @Override
    public String getRemotePath(FileLike localFile)
    {
        URI localUri = FileUtil.getAbsoluteCaseSensitiveFile(localFile).toURI();
        URI remote = RserveScriptEngine.makeLocalToRemotePath(_def, getWorkingDir(getContext()), localUri);
        return PathMapper.uriToPath(remote);
    }

    @Override
    public String getRemotePath(String local)
    {
        try
        {
            URI localUri = PathMapper.pathToUri(local);
            URI remote = RserveScriptEngine.makeLocalToRemotePath(_def, getWorkingDir(getContext()), localUri);
            return PathMapper.uriToPath(remote);
        }
        catch (URISyntaxException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }
}
