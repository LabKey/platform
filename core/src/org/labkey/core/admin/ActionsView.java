/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

package org.labkey.core.admin;

import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.util.Formats;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.ContainerManager;
import org.springframework.web.servlet.mvc.Controller;

import java.io.PrintWriter;
import java.util.Map;

class ActionsView extends HttpView
{
    private final boolean _summary;

    ActionsView(boolean summary)
    {
        _summary = summary;
    }

    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        if (!_summary)
            out.println(PageFlowUtil.button("Export").href(new ActionURL(AdminController.ExportActionsAction.class, ContainerManager.getRoot())));

        Map<String, Map<String, Map<String, SpringActionController.ActionStats>>> modules = ActionsHelper.getActionStatistics();

        out.print("<table id=\"actions\">");

        if (_summary)
            out.print("<tr align=left><th>Controller</th><th>Actions</th><th>Invoked</th><th>Coverage</th></tr>");
        else
            out.print("<tr align=left><th>Controller</th><th>Action</th><th>ActionType</th><th>Invocations</th><th>Cumulative Time</th><th>Average Time</th><th>Max Time</th></tr>");

        int totalActions = 0;
        int totalInvoked = 0;

        for (Map.Entry<String, Map<String, Map<String, SpringActionController.ActionStats>>> module : modules.entrySet())
        {
            Map<String, Map<String, SpringActionController.ActionStats>> controllers = module.getValue();

            for (Map.Entry<String, Map<String, SpringActionController.ActionStats>> controller : controllers.entrySet())
            {
                String controllerTd = "<td>" + controller.getKey() + "</td>";

                if (_summary)
                {
                    out.print("<tr>");
                    out.print(controllerTd);
                }

                int invokedCount = 0;

                Map<String, SpringActionController.ActionStats> actions = controller.getValue();

                for (Map.Entry<String, SpringActionController.ActionStats> action : actions.entrySet())
                {
                    if (!_summary)
                    {
                        out.print("<tr>");
                        out.print(controllerTd);
                        controllerTd = "<td>&nbsp;</td>";
                        out.print("<td>");
                        out.print(action.getKey());
                        out.print("</td>");

                        Class<? extends ActionType> type = action.getValue().getActionType();
                        out.print("<td>");
                        out.print(null != type ? type.getSimpleName() : "&nbsp;");
                        out.print("</td>");
                    }

                    SpringActionController.ActionStats stats = action.getValue();

                    if (stats.getCount() > 0)
                        invokedCount++;

                    if (_summary)
                        continue;

                    renderTd(out, stats.getCount());
                    renderTd(out, stats.getElapsedTime());
                    renderTd(out, 0 == stats.getCount() ? 0 : stats.getElapsedTime() / stats.getCount());
                    renderTd(out, stats.getMaxTime());

                    out.print("</tr>");
                }

                totalActions += actions.size();
                totalInvoked += invokedCount;

                double coverage = actions.isEmpty() ? 0 : invokedCount / (double)actions.size();

                if (!_summary)
                    out.print("<tr><td>&nbsp;</td><td>Action Coverage</td>");
                else
                {
                    out.print("<td>");
                    out.print(actions.size());
                    out.print("</td><td>");
                    out.print(invokedCount);
                    out.print("</td>");
                }

                out.print("<td>");
                out.print(Formats.percent1.format(coverage));
                out.print("</td></tr>");

                if (!_summary)
                    out.print("<tr><td colspan=6>&nbsp;</td></tr>");
            }
        }

        double totalCoverage = (0 == totalActions ? 0 : totalInvoked / (double)totalActions);

        if (_summary)
        {
            out.print("<tr><td colspan=4>&nbsp;</td></tr><tr><td>Total</td><td>");
            out.print(totalActions);
            out.print("</td><td>");
            out.print(totalInvoked);
            out.print("</td>");
        }
        else
        {
            out.print("<tr><td colspan=2>Total Action Coverage</td>");
        }

        out.print("<td>");
        out.print(Formats.percent1.format(totalCoverage));
        out.print("</td></tr>");
        out.print("</table>");
    }


    private void renderTd(PrintWriter out, Number d)
    {
        out.print("<td>");
        out.print(Formats.commaf0.format(d));
        out.print("</td>");
    }
}
