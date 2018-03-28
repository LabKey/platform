/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Core api proxy interface to RStudio premium module functionality
 */
public interface RStudioService
{
    String R_DOCKER_SANDBOX = "rDockerSandbox";
    String R_DOCKER_ENGINE ="R Docker Scripting Engine";
    String NO_RSTUDIO = "RStudio module is not present.";

    default boolean isConfigured()
    {
        return false;
    }

    default String getMount()
    {
        throw new UnsupportedOperationException(NO_RSTUDIO);
    }

    default void executeR(File scriptFile, String localWorkingDir, String remoteWorkingDir, FileFilter inputFiles) throws IOException
    {
        throw new UnsupportedOperationException(NO_RSTUDIO);
    }

    default boolean isEditInRStudioAvailable()
    {
        return false;
    }

    default Pair<String, String> editInRStudio(File scriptFile, String entityId, ViewContext viewContext, BindException errors)
    {
        throw new UnsupportedOperationException(NO_RSTUDIO);
    }

    // the no-explanation version, just return null if user is not eligible
    ActionURL getRStudioLink(User user);

    default void addRequiredLibrary(String library) {};

    default List<String> getRequiredLibraries() {return Collections.emptyList();};

    default boolean isUserEditingReportInRStudio(User user, String entityId)
    {
        return false;
    }

    default Pair<String, String> getReportRStudioUrl(ViewContext viewContext, String entityId)
    {
        return null;
    }

    default ActionURL getFinishReportUrl(Container container)
    {
        return null;
    }

    /**
     * Inject javascript by converting response stream to String and inject js string to html.
     * This method does not preserve non-string content, such as images,
     * @param servletContext
     * @param servletName
     * @param properties
     * @param injectJavascriptHook the js string to inject to html
     * @param capture True to use controller that supports getBody method
     * @return
     * @throws Exception
     */
    default Controller createInjectScriptHttpProxy(ServletContext servletContext, String servletName, Properties properties, String injectJavascriptHook, boolean capture) throws Exception
    {
        return null;
    }

    /**
     * Modify html content of response without losing non string content (images).
     * Unless html contains <img> tab contents, createInjectScriptHttpProxy is preferred for better performance.
     * @param servletContext
     * @param servletName
     * @param properties
     * @param searchReplacements List of pairs of search string and replacement string
     * @return
     * @throws Exception
     */
    default Controller createModifyHtmlHttpProxy(ServletContext servletContext, String servletName, Properties properties, List<Pair<String, String>> searchReplacements) throws Exception
    {
        return null;
    }

}
