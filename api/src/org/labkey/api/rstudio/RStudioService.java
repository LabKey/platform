/*
 * Copyright (c) 2016-2018 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Map;

/**
 * Core api proxy interface to RStudio premium module functionality
 */
public interface RStudioService
{
    String R_DOCKER_SANDBOX = "rDockerSandbox";
    String R_DOCKER_ENGINE = "R Docker Scripting Engine";
    String NO_RSTUDIO = "RStudio module is not present.";

    static RStudioService get()
    {
        return ServiceRegistry.get().getService(RStudioService.class);
    }

    static void setInstance(RStudioService impl)
    {
        ServiceRegistry.get().registerService(RStudioService.class, impl);
    }

    default boolean isConfigured()
    {
        return false;
    }

    default String getMount() //TODO remove after merge
    {
        throw new UnsupportedOperationException(NO_RSTUDIO);
    }

    default void executeR(File scriptFile, String localWorkingDir, String remoteWorkingDir, FileFilter inputFiles) throws IOException //TODO remove after merge
    {
        throw new UnsupportedOperationException(NO_RSTUDIO);
    }

    default boolean isEditInRStudioAvailable()
    {
        return false;
    }

    default Pair<String, String> editInRStudio(RReport report, ViewContext viewContext, BindException errors) throws Exception
    {
        throw new UnsupportedOperationException(NO_RSTUDIO);
    }

    // the no-explanation version, just return null if user is not eligible
    default ActionURL getRStudioLink(User user, Container container)
    {
        return null;
    }

    default HttpView getExportToRStudioView(QueryView.TextExportOptionsBean textBean)
    {
        return null;
    }

    default Map<String, Object> getRStudioEditorConfig(ViewContext viewContext, RReport report)
    {
        return null;
    }

    default String getInputDataProlog(ViewContext context, RReport rReport)
    {
        return null;
    }
}
