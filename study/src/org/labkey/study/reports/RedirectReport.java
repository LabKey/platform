/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
package org.labkey.study.reports;

import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.io.PrintWriter;

/**
 * User: migra
 * Date: Mar 7, 2006
 * Time: 3:00:54 PM
 */
public abstract class RedirectReport extends AbstractReport
{
    public static final String REDIRECT_URL = "redirectUrl";

    public HttpView renderReport(ViewContext viewContext)
    {
        return new RedirectView();
    }

    public String getParams()
    {
        return getDescriptor().getProperty(REDIRECT_URL);
    }

    public void setParams(String params)
    {
        getDescriptor().setProperty(REDIRECT_URL, params);
    }

    public void setUrl(ActionURL url)
    {
        setParams(url.toString());
    }

    public String getUrl(ViewContext c)
    {
        return getParams();
    }

    private class RedirectView extends HttpView
    {
        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            HttpView.throwRedirect(getUrl(getViewContext()));
        }
    }
}
