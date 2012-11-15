/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.labkey.api.study.assay.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: kevink
 * Date: Feb 10, 2009
 */
public class AssayResultsWebPartFactory extends AssayBaseWebPartFactory
{

    public AssayResultsWebPartFactory()
    {
        super("Assay Results");
    }

    public String getDescription()
    {
        return "This web part displays a list of results for a specific assay.";
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart, ExpProtocol protocol, boolean showButtons)
    {
        String dataRegionName = AssayProtocolSchema.DATA_TABLE_NAME + webPart.getIndex();
        AssayResultsView resultsView = new AssayResultsView(protocol, !showButtons, null, dataRegionName);
        resultsView.setTitleHref(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(portalCtx.getContainer(), protocol));
        resultsView.setTitle(protocol.getName() + " Results");
        return resultsView;
    }
}
