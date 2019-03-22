/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;


/**
 * Servlets and JSPs are really meant to be top-level entry points
 * using them as views is fairly expensive.  However, it is very useful for
 * prototyping, so here is a wrapper.
 */

public class ServletView extends HttpView
{
    protected boolean _forward = false;
    protected String _pathname;


    public ServletView(String path)
    {
        _pathname = path;
    }


    public ServletView(String url, ViewContext context)
    {
        super(context);
        _pathname = url;
    }


    public String getPath()
    {
        return _pathname;
    }


    @Override
    public void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        ViewContext context = getViewContext();
        for (Object o : context.entrySet())
        {
            Map.Entry entry = (Map.Entry) o;
            request.setAttribute(String.valueOf(entry.getKey()), entry.getValue());
        }

        RequestDispatcher r = request.getRequestDispatcher(_pathname);
        if (_forward)
            r.forward(request, response);
        else
            r.include(request, response);
    }


    public String toString()
    {
        return super.toString() + " URL=" + String.valueOf(_pathname);
    }


    public void setForward(boolean f)
    {
        _forward = f;
    }


    public boolean getForward()
    {
        return _forward;
    }
}
