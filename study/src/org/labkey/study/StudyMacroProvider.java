/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study;

import org.labkey.api.reports.Report;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.wiki.MacroProvider;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.view.DatasetsWebPartView;

import java.util.Map;

/**
 * User: Mark Igra
 * Date: Jun 27, 2006
 * Time: 2:16:44 PM
 */
public class StudyMacroProvider implements MacroProvider
{
    public HttpView getView(String name, Map<String, String> params, ViewContext parentContext)
    {
        if ("datasets".equals(name))
            return new DatasetsWebPartView();
        if ("view".equals(name))
        {
            try
            {
                Report report = null;
                String reportName = params.get("name");
                String reportIdStr;

                reportIdStr = params.get("id");
                int reportId;
                if (null != reportIdStr)
                {
                    reportId = Integer.parseInt(reportIdStr.trim());
                    report = ReportManager.get().getReport(parentContext.getContainer(), reportId);
                }

                if (null == report)
                    return new HtmlView("Could not find view with name = " + PageFlowUtil.filter(reportName) + " or id = " + reportIdStr + "<br>");


                HttpView view = report.renderReport(parentContext);
                //Turn off the title bar
                if (view instanceof WebPartView)
                    ((WebPartView) view).setFrame(WebPartView.FrameType.DIV);

                return view;
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        else
            return new HtmlView(null, "No such macro, study:" + PageFlowUtil.filter(name) + "<br>");
    }
}
