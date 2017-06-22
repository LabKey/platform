/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.settings;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Aug 6, 2008
 * Time: 10:44:58 AM
 */

// Used for linking to resources that have a container but aren't pageflow actions.  Examples include logos, favicons, and stylesheets.
public class ResourceURL extends URLHelper
{
    private static Pattern urlPattern = Pattern.compile("(/.*)?/([\\w-]*)\\.(.*)");

    public ResourceURL(String resource, Container c)
    {
        setContextPath(AppProps.getInstance().getParsedContextPath());
        _path = c.getParsedPath().append(Path.parse(resource));
        addParameter("revision", String.valueOf(AppProps.getInstance().getLookAndFeelRevision()));
    }

    public ResourceURL(String resource)
    {
        this(resource, ContainerManager.getRoot());
    }

    public ResourceURL(HttpServletRequest request) throws ServletException
    {
        Matcher m = urlPattern.matcher(request.getServletPath());
        if (!m.matches())
            throw new ServletException("invalid path");

        setContextPath(request.getContextPath());
        _path = Path.decode(request.getServletPath());
    }
}
