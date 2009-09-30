/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;

import java.util.Set;
import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jan 25, 2008
 */
public class ExperimentMembershipDisplayColumnFactory implements DisplayColumnFactory
{
    private ColumnInfo _expRowIdCol;
    private FieldKey _expRunFieldKey;
    private int _runId = -1;
    private int _expId = -1;

    public ExperimentMembershipDisplayColumnFactory(ColumnInfo expRowIdCol, int runId)
    {
        _expRowIdCol = expRowIdCol;
        _runId = runId;
    }

    public ExperimentMembershipDisplayColumnFactory(int expId, FieldKey expRunFieldKey)
    {
        _expId = expId;
        _expRunFieldKey = expRunFieldKey;
    }

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

        public void addQueryColumns(Set<ColumnInfo> queryCols)
        {
            super.addQueryColumns(queryCols);
            if (_expRowIdCol != null)
            {
                queryCols.add(_expRowIdCol);
            }
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if (!_renderedFunction)
            {
                ActionURL url = new ActionURL(ExperimentController.ToggleRunExperimentMembershipAction.class, ctx.getContainer());
                out.write("<script language=\"javascript\">\n" +
                        "LABKEY.requiresYahoo('connection');\n" +
                        "function toggleRunExperimentMembership(expId, runId, included, dataRegionName)\n" +
                        "{\n" +
                        "    var callback = { \n" +
                        "        success: function(o) { LABKEY.DataRegions[dataRegionName].showMessage('Run group information saved successfully.') },\n" +
                        "        failure: function(o) { LABKEY.DataRegions[dataRegionName].showMessage('<div class=\"labkey-error\">Run group information save failed.</div>') },\n" +
                        "    }\n" +
                        "    YAHOO.util.Connect.asyncRequest('GET', '" + url + "runId=' + runId + '&experimentId=' + expId + '&included=' + included, callback, null); \n" +
                        "};\n" +
                        "</script>");
                _renderedFunction = true;
            }

            out.write("<input type=\"checkbox\" name=\"experimentMembership\" ");
            if (ctx.getViewContext().hasPermission(ACL.PERM_UPDATE))
            {
                out.write(" onclick=\"javascript:toggleRunExperimentMembership(" + getExpId(ctx) + ", " + getRunId(ctx) + ", this.checked, '" + PageFlowUtil.filter(ctx.getCurrentRegion().getName()) + "');\"");
            }
            else
            {
                out.write("disabled=\"true\" ");
            }
            Boolean checked = (Boolean)getDisplayColumn().getValue(ctx);
            if (Boolean.TRUE.equals(checked))
            {
                out.write("checked=\"true\" ");
            }
            out.write("/>");
        }

        private int getExpId(RenderContext ctx)
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

        private int getRunId(RenderContext ctx)
        {
            if (_runId == -1)
            {
                ColumnInfo columnInfo = ctx.getFieldMap().get(getRunRowIdFieldKey());
                if (columnInfo != null)
                {
                    return ((Number)columnInfo.getValue(ctx)).intValue();
                }
                else
                {
                    return -1;
                }
            }
            else
            {
                return _runId;
            }
        }
    }
}
