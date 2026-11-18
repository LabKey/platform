/*
 * Copyright (c) 2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.remoterunner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.vfs.FileLike;

import java.io.FileFilter;
import java.io.IOException;

/**
 * Runs a script in a remote container over HTTP.
 *
 * The working directory is staged to object storage and handed to the runner as presigned URLs, so the runner holds no
 * credentials: each URL is scoped to a single object and expires. Implemented by the cloudServices module.
 */
public interface RemoteRunnerService
{
    static @Nullable RemoteRunnerService get()
    {
        return ServiceRegistry.get().getService(RemoteRunnerService.class);
    }

    static void setInstance(RemoteRunnerService impl)
    {
        ServiceRegistry.get().registerService(RemoteRunnerService.class, impl);
    }

    /** True when a runner endpoint and a staging bucket are both configured. */
    boolean isEnabled();

    /**
     * Tar {@code localWorkingDir}, run {@code scriptFile} against it in the remote runner, and unpack the result back
     * over {@code localWorkingDir}.
     *
     * The runner picks an interpreter from the script's extension, so this is not R-specific; an extension it does not
     * recognize is an error rather than a default.
     *
     * @param scriptFile      the script to run, already written into the working directory
     * @param localWorkingDir working directory on this server
     * @param inputFiles      which files in the working directory to send
     * @return how the script itself ended; a failure to stage or reach the runner throws instead
     */
    Result execute(FileLike scriptFile, String localWorkingDir, @Nullable FileFilter inputFiles)
            throws IOException;

    /**
     * @param status     "ok", "script_error" or "timeout"
     * @param returnCode the interpreter's exit code; null when it was killed for running too long
     */
    record Result(@NotNull String status, @Nullable Integer returnCode)
    {
        public boolean succeeded()
        {
            return "ok".equals(status);
        }
    }
}
