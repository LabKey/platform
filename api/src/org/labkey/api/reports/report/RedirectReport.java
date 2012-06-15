/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.labkey.api.data.Container;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/**
 * User: migra
 * Date: Mar 7, 2006
 * Time: 3:00:54 PM
 */
public abstract class RedirectReport extends AbstractReport
{
    public static final String REDIRECT_URL = ReportDescriptor.Prop.redirectUrl.name();

    public RedirectReport()
    {
    }

    public HttpView renderReport(ViewContext viewContext) throws Exception
    {
        return HttpView.redirect(getUrl(viewContext.getContainer()));
    }

    public void setUrl(URLHelper url)
    {
        String redirectUrl = url.getURIString();

        // XXX: Is there a better way to check if a link is local to this server?
        if (url.getScheme() == null || url.getHost() == null ||
                redirectUrl.startsWith(AppProps.getInstance().getBaseServerUrl()))
        {
            redirectUrl = url.getLocalURIString();
        }

        getDescriptor().setProperty(REDIRECT_URL, redirectUrl);
    }

    public String getUrl(Container c)
    {
        return getDescriptor().getProperty(REDIRECT_URL);
    }
}
