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

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart, ExpProtocol protocol, boolean showButtons)
    {
        Integer batchId = getBatchId(webPart);
        Integer runId = getRunId(webPart);
        // XXX: filtering by batch and run not yet implemented

        AssayResultsView resultsView = new AssayResultsView(protocol, !showButtons);
        resultsView.setTitleHref(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(portalCtx.getContainer(), protocol));
        resultsView.setTitle(protocol.getName() + " Results");
        return resultsView;
    }
}
