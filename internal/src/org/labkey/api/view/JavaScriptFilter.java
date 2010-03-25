/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.api.view;

import org.apache.commons.io.IOUtils;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Path;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.*;

public class JavaScriptFilter implements Filter
{
    private static boolean _cachingAllowed = AppProps.getInstance().isCachingAllowed();

    public void destroy()
    {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        if (!_cachingAllowed && null != AppProps.getInstance().getProjectRoot())
        {
            HttpServletRequest request = (HttpServletRequest)servletRequest;
            Path path = Path.parse(request.getServletPath());
            Resource r = ModuleLoader.getInstance().getCurrentModule().getModuleResource(path);
            if (r != null)
            {
                InputStream is = r.getInputStream();
                OutputStream os = servletResponse.getOutputStream();
                IOUtils.copy(is, os);

                os.close();
                is.close();
                return;
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    public void init(FilterConfig filterConfig) throws ServletException
    {
    }
}
