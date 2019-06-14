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

package org.labkey.query.reports.view;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.JspView;

import java.io.PrintWriter;

/**
 * User: Karl Lum
 * Date: Mar 2, 2008
 */
public class ReportsWebPartConfig extends HttpView
{
    private final Portal.WebPart _webPart;

    public ReportsWebPartConfig(Portal.WebPart webPart)
    {
        _webPart = webPart;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        JspView view = new JspView<>("/org/labkey/query/reports/view/reportsWebPartConfig.jsp", _webPart);
        include(view);
    }
}
