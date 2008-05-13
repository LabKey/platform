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
package org.labkey.study.view;

import org.labkey.api.view.*;
import org.labkey.api.query.QueryView;
import org.labkey.api.study.assay.AssayService;

/**
 * User: jeckels
 * Date: Jul 19, 2007
 */
public class AssayListWebPartFactory extends WebPartFactory
{
    public AssayListWebPartFactory()
    {
        super("Assay List");
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        QueryView listView = AssayService.get().createAssayListView(portalCtx, true);
        ActionURL url = portalCtx.cloneActionURL();
        url.deleteParameters();
        url.setPageFlow("assay");
        url.setAction("begin.view");
        listView.setTitle("Assay List");
        listView.setTitleHref(url.getLocalURIString());
        return listView;
    }
}
