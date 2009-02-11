/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.study.assay.AssayRunsView;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.view.*;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: jeckels
 * Date: Jul 19, 2007
 */
public class AssayRunsWebPartFactory extends AssayBaseWebPartFactory
{

    public AssayRunsWebPartFactory()
    {
        super("Assay Runs");
        this.addLegacyNames("Assay Details");
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart, ExpProtocol protocol, boolean showButtons)
    {
        Integer batchId = getBatchId(webPart);
        // XXX: filtering by batch not yet implemented

        AssayRunsView runsView = new AssayRunsView(protocol, !showButtons);
        runsView.setTitleHref(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(portalCtx.getContainer(), protocol));
        runsView.setTitle(protocol.getName() + " Runs");
        return runsView;
    }

}
