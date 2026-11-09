/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.InputBuilder;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.HtmlWriter;

import java.util.Set;

import static org.labkey.api.util.DOM.SCRIPT;

public class ExperimentMembershipDisplayColumnFactory implements DisplayColumnFactory
{
    private ColumnInfo _expRowIdCol;
    private FieldKey _expRunFieldKey;
    private long _runId = -1;
    private long _expId = -1;

    public ExperimentMembershipDisplayColumnFactory(ColumnInfo expRowIdCol, long runId)
    {
        _expRowIdCol = expRowIdCol;
        _runId = runId;
    }

    public ExperimentMembershipDisplayColumnFactory(long expId, FieldKey expRunFieldKey)
    {
        _expId = expId;
        _expRunFieldKey = expRunFieldKey;
    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new ExperimentMembershipDisplayColumn(colInfo);
    }

    private class ExperimentMembershipDisplayColumn extends DataColumn
    {
        private boolean _renderedFunction = false;

        private ExperimentMembershipDisplayColumn(ColumnInfo col)
        {
            super(col);
            setWidth("20");
        }

        @Override
        public boolean isFilterable()
        {
            return true;
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            if (_runId == -1)
            {
                keys.add(getRunRowIdFieldKey());
            }
        }

        private FieldKey getRunRowIdFieldKey()
        {
            return _expRunFieldKey == null ? FieldKey.fromParts("RowId") : new FieldKey(_expRunFieldKey, "RowId");
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> queryCols)
        {
            super.addQueryColumns(queryCols);
            if (_expRowIdCol != null)
            {
                queryCols.add(_expRowIdCol);
            }
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
        {
            if (!_renderedFunction)
            {
                ActionURL url = new ActionURL(ExperimentController.ToggleRunExperimentMembershipAction.class, ctx.getContainer());
                String script =
                    "function toggleRunExperimentMembership(expId, runId, included, dataRegionName)\n" +
                    "{\n" +
                    "    var config = { \n" +
                    "        success: function(o) { LABKEY.DataRegions[dataRegionName].showMessage('Run group information saved successfully.') },\n" +
                    "        failure: function(o) { LABKEY.DataRegions[dataRegionName].showMessage('<div class=\"labkey-error\">Run group information save failed.</div>') },\n" +
                    "        url: " + PageFlowUtil.jsString(url.getLocalURIString() + "?") + " + 'runId=' + runId + '&experimentId=' + expId + '&included=' + included,\n" +
                    "        method: 'POST'\n" +
                    "    }\n" +
                    "    LABKEY.Ajax.request(config); \n" +
                    "};\n";
                out.write(SCRIPT(JavaScriptFragment.unsafe(script)));
                _renderedFunction = true;
            }

            String id = HttpView.currentPageConfig().makeId("checkbox");
            long currentExpId = getExpId(ctx);
            long currentExpRunId = getRunId(ctx);
            ExpExperiment exp = ExperimentService.get().getExpExperiment(currentExpId);
            ExpRun run = ExperimentService.get().getExpRun(currentExpRunId);
            Boolean checked = (Boolean)getDisplayColumn().getValue(ctx);
            InputBuilder<?> checkbox = InputBuilder.checkbox().id(id).name("experimentMembership").checked(Boolean.TRUE.equals(checked));
            // Users need to be able to read the run group, and update the run itself
            if (run != null && exp != null &&
                    exp.getContainer().hasPermission(ctx.getViewContext().getUser(), ReadPermission.class) &&
                    run.getContainer().hasPermission(ctx.getViewContext().getUser(), UpdatePermission.class))
            {
                HttpView.currentPageConfig().addHandler(id, "click", "toggleRunExperimentMembership(" + currentExpId + ", " + getRunId(ctx) + ", this.checked, " + PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + ");");
            }
            else
            {
                checkbox.disabled(true);
            }
            out.write(checkbox);
        }

        private long getExpId(RenderContext ctx)
        {
            if (_expId == -1)
            {
                return ((Number)_expRowIdCol.getValue(ctx)).intValue();
            }
            else
            {
                return _expId;
            }
        }

        private long getRunId(RenderContext ctx)
        {
            if (_runId == -1)
            {
                ColumnInfo columnInfo = ctx.getFieldMap().get(getRunRowIdFieldKey());
                if (columnInfo != null)
                {
                    Object value = columnInfo.getValue(ctx);
                    if (value instanceof Number n)
                    {
                        return n.longValue();
                    }
                }
                return -1;
            }
            else
            {
                return _runId;
            }
        }
    }
}
