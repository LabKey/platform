/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.rstudio;

import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Core api proxy interface to RStudio premium module functionality
 */
public interface RStudioService
{
    String R_DOCKER_SANDBOX = "rDockerSandbox";

    default boolean isConfigured()
    {
        return false;
    }

    default String getMount()
    {
        throw new UnsupportedOperationException("RStudio module is not present.");
    }

    default void executeR(File scriptFile, String localWorkingDir, String remoteWorkingDir, FileFilter inputFiles) throws IOException
    {
        throw new UnsupportedOperationException("RStudio module is not present.");
    }

    // the no-explanation version, just return null if user is not eligible
    ActionURL getRStudioLink(User user);

    default void addRequiredLibrary(String library) {};

    default List<String> getRequiredLibraries() {return Collections.emptyList();};
}
