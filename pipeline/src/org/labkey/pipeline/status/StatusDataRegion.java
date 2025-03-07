/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.pipeline.status;

import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.HtmlWriter;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;

public class StatusDataRegion extends DataRegion
{
    private final Class<? extends ReadOnlyApiAction<?>> _apiAction;
    private final ActionURL _returnUrl;

    public StatusDataRegion(Class<? extends ReadOnlyApiAction<?>> apiAction, ActionURL returnUrl)
    {
        setShowPagination(false);
        setAllowHeaderLock(false); // 13731: disabling header locking due to async rendering issues
        _apiAction = apiAction;
        _returnUrl = returnUrl.clone();
        _returnUrl.deleteParameter(ActionURL.Param.returnUrl);
    }

    private void renderTab(Writer out, String text, ActionURL url, boolean selected) throws IOException
    {
        String selectStyle = "";
        if (selected)
            selectStyle = " class=\"labkey-frame\"";
        out.write("<td");
        out.write(selectStyle);
        out.write(">&nbsp;&nbsp;<a href=\"");
        out.write(url.getEncodedLocalURIString());
        out.write("\">");
        out.write(text);
        out.write("</a>&nbsp;&nbsp;</td>\n");
    }

    @Override
    protected void renderTable(RenderContext ctx, HtmlWriter out) throws SQLException, IOException
    {
        if (_apiAction == null)
        {
            super.renderTable(ctx, out);
            return;
        }

        String controller = SpringActionController.getControllerName(_apiAction);
        String action = SpringActionController.getActionName(_apiAction);
        PageConfig config = HttpView.currentPageConfig();

        Writer oldWriter = out.unwrap();

        oldWriter.write("<script type=\"text/javascript\" nonce=\"" + config.getScriptNonce() + "\">\n");
        oldWriter.write(
                "LABKEY.requiresExt4Sandbox(function() {\n" +
                    "LABKEY.requiresScript('pipeline/StatusUpdate.js', function(){\n" +
                        "if (!LABKEY.pipeline.statusUpdateInstance)\n" +
                            "LABKEY.pipeline.statusUpdateInstance = new LABKEY.pipeline.StatusUpdate(" + PageFlowUtil.jsString(controller) + "," + PageFlowUtil.jsString(action) + "," + PageFlowUtil.jsString(_returnUrl.toString()) + ");\n" +
                        "LABKEY.pipeline.statusUpdateInstance.start();\n" +
                    "});\n" +
                "});\n");
        oldWriter.write("</script>\n");

        ActionURL url = StatusController.urlShowList(ctx.getContainer(), false);
        ActionURL urlFilter = ctx.getSortFilterURLHelper();
        SimpleFilter filters = new SimpleFilter(urlFilter, getName());

        oldWriter.write("<table style=\"margin-bottom:10px;\">");
        oldWriter.write("<tr><td>Show:</td>");

        String name = "StatusFiles.Status~" + CompareType.NOT_IN.getPreferredUrlKey();
        String value = PipelineJob.TaskStatus.complete.toString() + ";" + PipelineJob.TaskStatus.cancelled.toString() + ";" + PipelineJob.TaskStatus.error.toString();
        url.deleteParameters();
        url.addParameter(name, value);
        boolean selected = value.equals(urlFilter.getParameter(name)) || PipelineQueryView.createCompletedFilter().equals(ctx.getBaseFilter());
        renderTab(oldWriter, "Running", url, selected);
        boolean selSeen = selected;

        name = "StatusFiles.Status~eq";
        value = PipelineJob.TaskStatus.error.toString();
        url.deleteParameters();
        url.addParameter(name, value);
        selected = !selSeen && value.equals(urlFilter.getParameter(name));
        renderTab(oldWriter, "Errors", url, selected);

        name = "StatusFiles.Status~eq";
        value = PipelineJob.TaskStatus.cancelled.toString();
        url.deleteParameters();
        url.addParameter(name, value);
        selected = !selSeen && value.equals(urlFilter.getParameter(name));
        renderTab(oldWriter, "Cancelled", url, selected);

        selSeen = selSeen || selected;
        url.deleteParameters();
        renderTab(oldWriter, "All", url, filters.getClauses().isEmpty() && !selSeen);

        oldWriter.write("</tr></table>\n");
        oldWriter.write("<div id=\"statusFailureDiv\" class=\"labkey-error\" style=\"display: none\"></div>");
        oldWriter.write("<div id=\"statusRegionDiv\">");

        super.renderTable(ctx, out);

        oldWriter.write("</div>");
    }
}
