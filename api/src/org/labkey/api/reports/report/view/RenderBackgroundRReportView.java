/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.RReportJob;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.VBox;

import java.io.*;
import java.util.Collection;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Feb 21, 2008
 */
public class RenderBackgroundRReportView extends HttpView
{
    private RReport _report;

    public RenderBackgroundRReportView(RReport report)
    {
        _report = report;        
    }

    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        if (_report != null)
        {
            File logFile = new File(_report.getReportDir(this.getViewContext().getContainer().getId()), RReportJob.LOG_FILE_NAME);
            VBox view = new VBox();
            view.addView(new JspView<>("/org/labkey/api/reports/report/view/ajaxReportRenderBackground.jsp", _report));

            if (logFile.exists())
            {
                PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile);
                if (statusFile != null)
                {
                    File filePath = new File(statusFile.getFilePath());
                    File substitutionMap = new File(filePath.getParentFile(), RReport.SUBSTITUTION_MAP);

                    // if the job is complete, show the results of the job
                    if (substitutionMap.exists())
                    {
                        Collection<ParamReplacement> outputSubst = ParamReplacementSvc.get().fromFile(substitutionMap);
                        VBox innerView = new VBox();
                        view.addView(innerView);
                        RReport.renderViews(_report, view, outputSubst, false);
                    }
                }
            }
            include(view);
        }
    }
}
