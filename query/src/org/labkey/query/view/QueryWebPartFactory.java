/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.query.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.QueryWebPart;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

public class QueryWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public QueryWebPartFactory()
    {
        super("Query", true, true);
    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        QueryWebPart ret = new QueryWebPart(portalCtx, webPart);
        populateProperties(ret, webPart.getPropertyMap());
        return ret;
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>("/org/labkey/query/view/editQueryWebPart.jsp", webPart);
    }
}
