/*
 * Copyright (c) 2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.remoterunner;

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
     * @param scriptFile      the script to run, already written into the working directory
     * @param localWorkingDir working directory on this server
     * @param remoteWorkingDir path the runner will see, used for path mapping
     * @param inputFiles      which files in the working directory to send
     */
    void executeR(FileLike scriptFile, String localWorkingDir, String remoteWorkingDir, @Nullable FileFilter inputFiles)
            throws IOException;
}
