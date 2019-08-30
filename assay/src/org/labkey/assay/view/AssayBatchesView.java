/*
 * Copyright (c) 2019 LabKey Corporation
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

import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.AssayView;
import org.labkey.api.assay.query.BatchListQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.assay.actions.AssayBatchDetailsAction;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

        BatchListQueryView batchesView = new BatchListQueryView(protocol, schema, settings)
        {
            @Override
            public List<DisplayColumn> getDisplayColumns()
            {
                List<DisplayColumn> ret = super.getDisplayColumns();
                String key = PageFlowUtil.urlProvider(AssayUrls.class).getBatchIdFilterParam();

                // Need to make sure that we keep the same container filter after following the link
                ExpExperimentTable tableInfo = (ExpExperimentTable)getTable();
                ActionURL runsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(context.getContainer(), protocol, tableInfo.getContainerFilter());
                DetailsURL expr = new DetailsURL(runsURL, Collections.singletonMap(key, "RowId"));

                // find the Name column and update URL
                ColumnInfo name = tableInfo.getColumn(ExpExperimentTable.Column.Name);
                ret.stream().filter(dc -> dc.getColumnInfo()==name).forEach(dc -> dc.setURLExpression(expr));
                return ret;
            }
        };

        if (provider.hasCustomView(ExpProtocol.AssayDomainTypes.Batch, true))
        {
            ActionURL detailsURL = new ActionURL(AssayBatchDetailsAction.class, context.getContainer());
            detailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<>();
            params.put("batchId", "RowId");

            batchesView.setShowDetailsColumn(true);
            batchesView.setDetailsURL(new DetailsURL(detailsURL, params));
        }

        setupViews(batchesView, minimizeLinks, provider, protocol);
    }
}
