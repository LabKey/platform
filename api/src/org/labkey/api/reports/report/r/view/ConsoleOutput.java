package org.labkey.api.reports.report.r.view;

import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.Report;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class ConsoleOutput extends AbstractParamReplacement
{
    public static final String ID = "consoleout:";

    public ConsoleOutput()
    {
        super(ID);
    }

    public File convertSubstitution(File directory) throws Exception
    {
        if (directory != null)
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.txt", directory);
        else
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.txt");

        return _file;
    }

    public HttpView render(ViewContext context)
    {
        ROutputView view = new TextOutput.TextOutputView(this);
        view.setLabel("Console output");
        if (HttpView.currentContext().get(Report.renderParam.reportWebPart.name()) != null)
            view.setCollapse(true);

        return view;
    }
}
