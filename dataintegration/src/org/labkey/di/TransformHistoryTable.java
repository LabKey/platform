/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.di;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.di.view.DataIntegrationController;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: Dax
 * Date: 9/13/13
 * Time: 12:27 PM
 */
public class TransformHistoryTable extends TransformBaseTable
{
    public TransformHistoryTable(UserSchema schema)
    {
        super(schema, DataIntegrationQuerySchema.TRANSFORMHISTORY_TABLE_NAME);
        _sql = new SQLFragment();
        _sql.append(getBaseSql());
        _sql.append(getWhereClause("t"));
        addBaseColumns();

        //
        // if we decide to move to a run details view then the history table should
        // link to that instead
        //
        // history table should link to filtered run table for transform details
        String colName = getNameMap().get("TransformId");
        ColumnInfo transformId = getColumn(colName);
        Map<String, String> params = new HashMap<>();
        params.put("transformRunId", "TransformRunId");
        params.put("transformId", colName);
        DetailsURL detailsUrl = new DetailsURL(new ActionURL(DataIntegrationController.viewTransformDetailsAction.class, null), params);
        transformId.setURL(detailsUrl);

        // Add links to job and experiment run details
        ColumnInfo job = getColumn(getNameMap().get("JobId"));
        job.setHidden(false);
        job.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new JobColumn(colInfo);
            }
        });

        ColumnInfo exp = getColumn(getNameMap().get("ExpRunId"));
        exp.setHidden(false);
        exp.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ExpColumn(colInfo);
            }
        });
    }

    @Override
    protected HashMap<String, String> buildNameMap()
    {
        HashMap<String, String> colMap = super.buildNameMap();
        colMap.put("StartTime", "DateRun");
        colMap.put("Status", "Status");
        return colMap;
    }

    public static class JobColumn extends DataColumn
    {
        FieldKey _jobFieldKey;

        public JobColumn(ColumnInfo job)
        {
            super(job);
            _jobFieldKey = job.getFieldKey();
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(_jobFieldKey);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String dataRegionName = ctx.getCurrentRegion() != null ? ctx.getCurrentRegion().getName() : null;
            Integer jobId = ctx.get(_jobFieldKey, Integer.class);

            if (null != jobId && null != dataRegionName)
            {
                String jobDetailsLink = TransformManager.get().getJobDetailsLink(ctx.getContainer(), jobId, "Job Details", true);
                if (null != jobDetailsLink)
                    out.write(jobDetailsLink);
            }
        }
    }

    public static class ExpColumn extends DataColumn
    {
        FieldKey _expFieldKey;

        public ExpColumn(ColumnInfo exp)
        {
            super(exp);
            _expFieldKey = exp.getFieldKey();
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(_expFieldKey);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String dataRegionName = ctx.getCurrentRegion() != null ? ctx.getCurrentRegion().getName() : null;
            Integer runId = ctx.get(_expFieldKey, Integer.class);
            if (null != runId && null != dataRegionName)
            {
                ActionURL detailsAction = PageFlowUtil.urlProvider(ExperimentUrls.class).getRunTextURL(ctx.getContainer(), runId);
                String href = detailsAction.toString();
                out.write(PageFlowUtil.textLink("Run Details", href));
            }
        }
    }
}
