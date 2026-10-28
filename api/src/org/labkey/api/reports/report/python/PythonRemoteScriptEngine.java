/*
 * Copyright (c) 2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.reports.report.python;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.remoterunner.RemoteRunnerService;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.RemoteWorkingDirScriptEngine;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import java.io.FileFilter;
import java.nio.file.Path;

/** Runs Python in the remote script runner, with the working directory staged through object storage. */
public class PythonRemoteScriptEngine extends PythonScriptEngine implements RemoteWorkingDirScriptEngine
{
    private static final Logger LOG = LogHelper.getLogger(PythonRemoteScriptEngine.class, "Remote Python script engine");

    private final RemoteRunnerService _service;

    public PythonRemoteScriptEngine(@NotNull ExternalScriptEngineDefinition def, @NotNull RemoteRunnerService service)
    {
        super(def);
        _service = service;
    }

    @Override
    protected Object eval(FileLike scriptFile, ScriptContext context) throws ScriptException
    {
        StringBuffer output = new StringBuffer();
        RemoteRunnerService.Result result;
        try (CustomTiming t = MiniProfiler.custom("remoteRunner", "execute python in remote runner"))
        {
            result = _service.execute(scriptFile, getWorkingDir(context).toNioPathForRead().toString(), inputFiles());
            appendConsoleOutput(context, output);
        }
        catch (Exception e)
        {
            // Message only: the chain can carry a presigned URL, and this text renders for anyone who can run the report.
            LOG.error("Remote runner failed for script '{}'", scriptFile.getName(), e);
            throw new ScriptException("An error occurred when running the script '" + scriptFile.getName()
                    + "': " + e.getMessage());
        }

        if (!result.succeeded())
            throw new ScriptException("An error occurred when running the script '" + scriptFile.getName() + "' ("
                    + result.status() + (result.returnCode() == null ? "" : ", exit code: " + result.returnCode()) + ").\n" + output);
        return output.toString();
    }

    /** The runner's working directory is a fresh temp directory, so only paths relative to it survive the trip. */
    @Override
    public String getRemotePath(FileLike localFile)
    {
        Path relative = getWorkingDir(getContext()).toNioPathForRead().relativize(localFile.toNioPathForRead()).normalize();
        if (relative.startsWith(".."))
            throw new IllegalArgumentException("'" + localFile.getName() + "' is outside the working directory, so the remote runner cannot see it");
        return "./" + FilenameUtils.separatorsToUnix(relative.toString());
    }

    // Transform and pipeline scripts get appserver paths in runProperties, which the runner can't see
    @Override
    public boolean supportsContext(LabKeyScriptEngineManager.EngineContext context)
    {
        return context != LabKeyScriptEngineManager.EngineContext.pipeline;
    }

    @Override
    protected @Nullable String getPackageCaptureEpilog(ScriptContext context)
    {
        return PACKAGE_CAPTURE_EPILOG.formatted(PACKAGES_FILE);
    }

    private static FileFilter inputFiles()
    {
        return pathname -> pathname.isFile() &&
                ("py".equalsIgnoreCase(FilenameUtils.getExtension(pathname.getName()))
                        || ScriptEngineReport.DATA_INPUT.equals(pathname.getName()));
    }
}
