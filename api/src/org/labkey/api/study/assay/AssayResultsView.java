/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;

/**
 * A composite of a header section and a QueryView for the results below
 * User: kevink
 */
public class AssayResultsView extends AssayView
{
    public AssayResultsView(ExpProtocol protocol, boolean minimizeLinks, BindException errors)
    {
        this(protocol, minimizeLinks, errors, AssayProtocolSchema.DATA_TABLE_NAME);
    }

    public AssayResultsView(ExpProtocol protocol, boolean minimizeLinks, BindException errors, String dataRegionName)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        AssayProtocolSchema schema = provider.createProtocolSchema(getViewContext().getUser(), getViewContext().getContainer(), protocol, null);
        QuerySettings settings = schema.getSettings(getViewContext(), dataRegionName, AssayProtocolSchema.DATA_TABLE_NAME);
        ResultsQueryView resultsView = schema.createDataQueryView(getViewContext(), settings, errors);
        if (resultsView == null)
        {
            throw new NotFoundException("No results QueryView available for '" + protocol.getName() + "' of type '" + provider+ "'");
        }
        setupViews(resultsView, minimizeLinks, provider, protocol);
    }
}
