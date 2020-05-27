/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.ResponseHelper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class StaticContentCachingFilter implements Filter
{
    private static boolean _cachingAllowed = AppProps.getInstance().isCachingAllowed();

    @Override
    public void destroy()
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest)servletRequest;
        String path = null != request.getServletPath() ? request.getServletPath().toLowerCase() : "";
        boolean cachableJsFile = path.contains("/ext-")
                                || path.contains("/tiny_mce/")
                                || path.contains("/tinymce3/");

        if (cachableJsFile || _cachingAllowed)
        {
            HttpServletResponse response = (HttpServletResponse)servletResponse;
            ResponseHelper.setPublicStatic(response, 5);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void init(FilterConfig filterConfig)
    {
    }
}
