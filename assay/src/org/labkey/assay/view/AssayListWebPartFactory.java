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
package org.labkey.assay.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

/**
 * User: jeckels
 * Date: Jul 19, 2007
 */
public class AssayListWebPartFactory extends BaseWebPartFactory
{
    public AssayListWebPartFactory()
    {
        super("Assay List");
    }

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        WebPartView listView = AssayService.get().createAssayListView(portalCtx, true, null);
        ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getBeginURL(portalCtx.getContainer());
        listView.setTitle("Assay List");
        listView.setTitleHref(url);
        return listView;
    }
}
