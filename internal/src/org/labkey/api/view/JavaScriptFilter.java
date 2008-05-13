/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.util.AppProps;
import org.labkey.api.module.ModuleLoader;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            File file = ModuleLoader.searchModuleSourceForFile(AppProps.getInstance().getProjectRoot(), "/webapp" + request.getServletPath());
            ((HttpServletResponse)servletResponse).setHeader("cache-control", "private");

            if (null != file)
            {
                byte[] buf = new byte[4096];
                InputStream is = new FileInputStream(file);
                OutputStream os = servletResponse.getOutputStream();

                for(int len; (len=is.read(buf))!=-1; )
                    os.write(buf,0,len);

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
