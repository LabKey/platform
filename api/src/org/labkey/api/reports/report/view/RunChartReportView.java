package org.labkey.api.reports.report.view;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.HtmlView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public class RunChartReportView extends RunReportView
{
    private Report _report;
    protected int _reportId;

    public RunChartReportView(Report report)
    {
        _report = report;
        if (_report != null)
            _reportId = _report.getDescriptor().getReportId();
    }
    
    protected Report getReport() throws Exception
    {
        return _report;
    }

    protected List<TabInfo> getTabList()
    {
        ActionURL url = getViewContext().cloneActionURL();

        List<TabInfo> tabs = new ArrayList<TabInfo>();
        tabs.add(new TabInfo(TAB_VIEW, TAB_VIEW, url));
        tabs.add(new TabInfo(TAB_DATA, TAB_DATA, url));

        return tabs;
    }

    protected HttpView getTabView(String tabId) throws Exception
    {
        if (TAB_VIEW.equals(tabId))
        {
            return new HtmlView(ChartUtil.getShowReportTag(getViewContext(), getReport()));
        }
        else if (TAB_DATA.equals(tabId))
        {
            return getReport().renderDataView(getViewContext());
        }
        return null; 
    }
}
