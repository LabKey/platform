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

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayBatchesView;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

/**
 * User: kevink
 * Date: Feb 10, 2009
 */
public class AssayBatchesWebPartFactory extends AssayBaseWebPartFactory
{
    public AssayBatchesWebPartFactory()
    {
        super("Assay Batches");
    }

    public String getDescription()
    {
        return "This web part displays a list of batches for a specific assay.";
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart, ExpProtocol protocol, boolean showButtons)
    {
        String dataRegionName = AssayProtocolSchema.BATCHES_TABLE_NAME + webPart.getIndex();
        AssayBatchesView batchesView = new AssayBatchesView(protocol, !showButtons, dataRegionName);
        batchesView.setTitleHref(PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(portalCtx.getContainer(), protocol, null));
        batchesView.setTitle(protocol.getName() + " Batches");
        return batchesView;
    }
}
