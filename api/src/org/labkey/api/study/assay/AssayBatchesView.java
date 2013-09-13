/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.study.actions.AssayBatchDetailsAction;
import org.labkey.api.study.actions.ShowSelectedRunsAction;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A composite of a header section and a QueryView for the batches below
 */
public class AssayBatchesView extends AssayView
{
    public AssayBatchesView(ExpProtocol protocol, boolean minimizeLinks)
    {
        this(protocol, minimizeLinks, AssayProtocolSchema.BATCHES_TABLE_NAME);
    }

    public AssayBatchesView(final ExpProtocol protocol, boolean minimizeLinks, String dataRegionName)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        ViewContext context = getViewContext();

        AssayProtocolSchema schema = provider.createProtocolSchema(context.getUser(), context.getContainer(), protocol, null);
        QuerySettings settings = schema.getSettings(context, dataRegionName, AssayProtocolSchema.BATCHES_TABLE_NAME);

        BatchListQueryView batchesView = new BatchListQueryView(protocol, schema, settings);

        // Unfortunately this seems to be the best way to figure out the name of the URL parameter to filter by batch id
        ActionURL fakeURL = new ActionURL(ShowSelectedRunsAction.class, context.getContainer());
        fakeURL.addFilter(AssayProtocolSchema.RUNS_TABLE_NAME,
                AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.EQUAL, "${RowId}");
        String key = fakeURL.getParameters().get(0).getKey();

        // Need to make sure that we keep the same container filter after following the link
        ExpExperimentTable tableInfo = (ExpExperimentTable)batchesView.getTable();
        ActionURL runsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(context.getContainer(), protocol, tableInfo.getContainerFilter());
        DetailsURL expr = new DetailsURL(runsURL, Collections.singletonMap(key,"RowId"));
        tableInfo.getColumn(ExpExperimentTable.Column.Name).setURL(expr);

        if (provider.hasCustomView(ExpProtocol.AssayDomainTypes.Batch, true))
        {
            ActionURL detailsURL = new ActionURL(AssayBatchDetailsAction.class, context.getContainer());
            detailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<>();
            params.put("batchId", "RowId");

            batchesView.setShowDetailsColumn(true);
            AbstractTableInfo table = (AbstractTableInfo)batchesView.getTable();
            table.setDetailsURL(new DetailsURL(detailsURL, params));
        }

        setupViews(batchesView, minimizeLinks, provider, protocol);
    }
}
