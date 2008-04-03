package org.labkey.api.reports.report;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 29, 2008
 */
public interface ReportUrls extends UrlProvider
{
    ActionURL urlDownloadData(Container c);
    ActionURL urlRunReport(Container c);
    ActionURL urlSaveRReportState(Container c);
    ActionURL urlUpdateRReportState(Container c);
    ActionURL urlDesignChart(Container c);
    ActionURL urlCreateRReport(Container c);
    ActionURL urlStreamFile(Container c);
}
