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
package org.labkey.pipeline.status;

import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;

import java.sql.SQLException;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.SCRIPT;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;
import static org.labkey.api.util.DOM.id;

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

    private void renderTab(HtmlWriter out, String text, ActionURL url, boolean selected)
    {
        TD(
            cl(selected, "labkey-frame"),
            HtmlString.NBSP,
            HtmlString.NBSP,
            LinkBuilder.simpleLink(text, url),
            HtmlString.NBSP,
            HtmlString.NBSP
        ).appendTo(out);
    }

    @Override
    protected void renderTable(RenderContext ctx, HtmlWriter out) throws SQLException
    {
        if (_apiAction == null)
        {
            super.renderTable(ctx, out);
            return;
        }

        String controller = SpringActionController.getControllerName(_apiAction);
        String action = SpringActionController.getActionName(_apiAction);

        SCRIPT(JavaScriptFragment.unsafe(
            "LABKEY.requiresExt4Sandbox(function() {\n" +
            "   LABKEY.requiresScript('pipeline/StatusUpdate.js', function(){\n" +
            "       if (!LABKEY.pipeline.statusUpdateInstance)\n" +
            "           LABKEY.pipeline.statusUpdateInstance = new LABKEY.pipeline.StatusUpdate(" + PageFlowUtil.jsString(controller) + "," + PageFlowUtil.jsString(action) + "," + PageFlowUtil.jsString(_returnUrl.toString()) + ");\n" +
            "       LABKEY.pipeline.statusUpdateInstance.start();\n" +
            "   });\n" +
            "});\n")
        ).appendTo(out);

        ActionURL url = StatusController.urlShowList(ctx.getContainer(), false);
        ActionURL urlFilter = ctx.getSortFilterURLHelper();
        SimpleFilter filters = new SimpleFilter(urlFilter, getName());

        TABLE(
            at(style, "margin-bottom:10px;"),
            TR(
                TD("Show:"),
                (DOM.Renderable) ret -> {
                    String name = "StatusFiles.Status~" + CompareType.NOT_IN.getPreferredUrlKey();
                    String value = PipelineJob.TaskStatus.complete + ";" + PipelineJob.TaskStatus.cancelled + ";" + PipelineJob.TaskStatus.error;
                    url.deleteParameters();
                    url.addParameter(name, value);
                    boolean selected = value.equals(urlFilter.getParameter(name)) || PipelineQueryView.createCompletedFilter().equals(ctx.getBaseFilter());
                    renderTab(out, "Running", url, selected);
                    boolean selSeen = selected;

                    name = "StatusFiles.Status~eq";
                    value = PipelineJob.TaskStatus.error.toString();
                    url.deleteParameters();
                    url.addParameter(name, value);
                    selected = !selSeen && value.equals(urlFilter.getParameter(name));
                    renderTab(out, "Errors", url, selected);

                    name = "StatusFiles.Status~eq";
                    value = PipelineJob.TaskStatus.cancelled.toString();
                    url.deleteParameters();
                    url.addParameter(name, value);
                    selected = !selSeen && value.equals(urlFilter.getParameter(name));
                    renderTab(out, "Cancelled", url, selected);

                    selSeen = selSeen || selected;
                    url.deleteParameters();
                    renderTab(out, "All", url, filters.getClauses().isEmpty() && !selSeen);

                    return ret;
                }
            )
        ).appendTo(out);

        DIV(
            id("statusFailureDiv").
            cl("labkey-error").
            at(style, "display: none")
        ).appendTo(out);

        DIV(
            id("statusRegionDiv"),
            (DOM.Renderable) ret -> {
                try
                {
                    super.renderTable(ctx, out);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
                return ret;
            }
        ).appendTo(out);
    }
}
