package org.labkey.study.reports;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 17, 2006
 */
public abstract class AbstractReportView extends AbstractReport implements ReportManager.ReportView
{
    private Integer _showWithDataset;
    private String _params;
    protected String _reportType;
    protected Report[] _reports;
    protected Container _container;

    public Integer getShowWithDataset() {return _showWithDataset;}
    public void setShowWithDataset(Integer dataset) {_showWithDataset = dataset;}

    public Container getContainer(){return _container;}
    public void setContainer(Container container){_container = container;}
    
    public String getParams() {return _params;}
    public void setParams(String params) {_params = params;}

    public String getReportViewType(){return _reportType;}
    public void setReports(org.labkey.api.reports.Report[] reports){_reports = reports;}
}
