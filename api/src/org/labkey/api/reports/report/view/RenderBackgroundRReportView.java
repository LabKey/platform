package org.labkey.api.reports.report.view;

import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.RReportJob;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.common.util.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
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
            File logFile = new File(_report.getReportDir(), RReportJob.LOG_FILE_NAME);
            if (logFile.exists())
            {
                PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile.getAbsolutePath());
                if (statusFile != null)
                {
                    File filePath = new File(statusFile.getFilePath());
                    File substitutionMap = new File(filePath.getParentFile(), RReport.SUBSTITUTION_MAP);

                    if (substitutionMap != null && substitutionMap.exists())
                    {
                        List<ParamReplacement> outputSubst = ParamReplacementSvc.get().fromFile(substitutionMap);
                        VBox view = new VBox();
                        RReport.renderViews(_report, view, outputSubst, false);

                        include(view);
                    }
                }
            }
        }
    }
}
