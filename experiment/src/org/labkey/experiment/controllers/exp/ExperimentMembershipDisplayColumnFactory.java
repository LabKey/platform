package org.labkey.experiment.controllers.exp;

import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.exp.ExperimentRunListView;

import java.util.Set;
import java.util.Collections;
import java.util.Map;
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
    private ColumnInfo _expRunRowIdCol;
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

        public void addQueryColumns(Set<ColumnInfo> queryCols)
        {
            super.addQueryColumns(queryCols);
            if (_runId == -1)
            {
                FieldKey runRowIdFieldKey = _expRunFieldKey == null ? FieldKey.fromParts("RowId") :new FieldKey(_expRunFieldKey, "RowId");
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(getColumnInfo().getParentTable(), Collections.singleton(runRowIdFieldKey));
                _expRunRowIdCol = cols.get(runRowIdFieldKey);
                assert _expRunRowIdCol != null;
                queryCols.add(_expRunRowIdCol);
            }
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
                        "function safeSetRunExperimentMembershipFeedback(message)\n" +
                        "{\n" +
                        "    var statusElement = document.getElementById('" + ExperimentRunListView.STATUS_ELEMENT_ID + "');\n" +
                        "    if (statusElement != null)\n" +
                        "    {\n" +
                        "        statusElement.innerHTML = message;\n" + 
                        "    }\n" +
                        "};\n" +
                        "function toggleRunExperimentMembership(expId, runId, included)\n" +
                        "{\n" +
                        "    safeSetRunExperimentMembershipFeedback('');\n" + 
                        "    var callback = { \n" +
                        "        success: function(o) { safeSetRunExperimentMembershipFeedback('Run group information saved successfully.') },\n" +
                        "        failure: function(o) { safeSetRunExperimentMembershipFeedback('Run group information save failed.') }\n" +
                        "    }\n" +
                        "    YAHOO.util.Connect.asyncRequest('GET', '" + url + "runId=' + runId + '&experimentId=' + expId + '&included=' + included, callback, null); \n" +
                        "};\n" +
                        "</script>");
                _renderedFunction = true;
            }

            out.write("<input type=\"checkbox\" name=\"experimentMembership\" ");
            if (ctx.getViewContext().hasPermission(ACL.PERM_UPDATE))
            {
                out.write(" onclick=\"javascript:toggleRunExperimentMembership(" + getExpId(ctx) + ", " + getRunId(ctx) + ", this.checked);\"");
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
                return ((Number)_expRunRowIdCol.getValue(ctx)).intValue();
            }
            else
            {
                return _runId;
            }
        }
    }
}
