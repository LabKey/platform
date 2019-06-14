/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.ActionsHelper;
import org.labkey.api.util.CSRFException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ActionsExceptionsView extends HttpView
{
    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        Map<String, Map<String, Map<String, SpringActionController.ActionStats>>> modules = ActionsHelper.getActionStatistics();

        out.print("<table id=\"actions\" class=\"labkey-data-region-legacy labkey-show-borders\">");

        out.print("<tr><td class=\"labkey-column-header\">Controller</td>");
        out.print("<td class=\"labkey-column-header\">Action</td>");
        out.print("<td class=\"labkey-column-header\">Exceptions</td></tr>\n");

        int rowCount = 0;
        int exceptionCount = 0;

        for (Map.Entry<String, Map<String, Map<String, SpringActionController.ActionStats>>> module : modules.entrySet())
        {
            Map<String, Map<String, SpringActionController.ActionStats>> controllers = module.getValue();

            for (Map.Entry<String, Map<String, SpringActionController.ActionStats>> controller : controllers.entrySet())
            {
                String controllerTd = "<td>" + controller.getKey() + "</td>";

                Map<String, SpringActionController.ActionStats> actions = controller.getValue();

                for (Map.Entry<String, SpringActionController.ActionStats> action : actions.entrySet())
                {
                    SpringActionController.ActionStats stats = action.getValue();
                    if (!stats.hasExceptions())
                        continue;

                    HashSet<String> dupes = new HashSet<String>();

                    rowCount++;
                    out.print("<tr class=\"" + (rowCount % 2 == 0 ? "labkey-alternate-row" : "labkey-row") + "\">");
                    out.print(controllerTd);
                    controllerTd = "<td>&nbsp;</td>";
                    out.print("<td>");
                    out.print(action.getKey());
                    out.print("</td>");

                    out.print("<td>");
                    List<Exception> exceptions = stats.getExceptions();
                    for (Exception ex : exceptions)
                    {
                        exceptionCount++;
                        String html = PageFlowUtil.filter(ex.getMessage());
                        if (ex instanceof CSRFException)
                        {
                            html = "CSRFException";
                            if (StringUtils.isNotBlank(((CSRFException)ex).getReferer()))
                                html += " -- referer: " + PageFlowUtil.filter(((CSRFException) ex).getReferer());
                        }
                        if (dupes.add(html))
                        {
                            out.print("<p>");
                            out.print(html);
                            out.print("</p>");
                        }
                    }
                    out.print("</td>");
                    out.print("<tr>\n");
                }
            }
        }
        out.print("</table>");

        if (exceptionCount == 0)
            out.print("<b><span id='exceptionCount'>no exceptions</span></b>");
        else
            out.print("<span id='exceptionCount'>" + exceptionCount +  " exception(s)</span></b>");
    }
}
