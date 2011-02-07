package org.labkey.api.visualization;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.view.ActionURL;

public interface VisualizationUrls extends UrlProvider
{
    public ActionURL getTimeChartDesignerURL(Container container);
    public ActionURL getTimeChartDesignerURL(Container container, String schemaName, String queryName);
    public ActionURL getTimeChartDesignerURL(Container container, Report report);
    public ActionURL getViewerURL(Container container, Report report);
}
