/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.query.reports;

import org.labkey.api.view.*;
import org.labkey.query.reports.view.ReportsWebPart;
import org.labkey.query.reports.view.ReportsWebPartConfig;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 2, 2008
 */
public class ReportsWebPartFactory extends WebPartFactory
{
    public ReportsWebPartFactory()
    {
        super("Report", null, true, true);
    }
    
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        ReportsWebPart wp = new ReportsWebPart(portalCtx, webPart);
        populateProperties(wp, webPart.getPropertyMap());

        return wp;
    }

    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new ReportsWebPartConfig(webPart);
    }
}
