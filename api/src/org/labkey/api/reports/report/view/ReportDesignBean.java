package org.labkey.api.reports.report.view;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.FormArrayList;
import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.actions.ReportForm;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.common.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 4, 2007
 */
public class ReportDesignBean extends ReportForm
{
    protected String _queryName;
    protected String _schemaName;
    protected String _viewName;
    protected String _dataRegionName;
    protected String _filterParam;
    protected int _reportId = -1;
    protected int _owner = -1;
    protected String _reportType;
    protected String _reportName;
    protected String _reportDescription;
    protected ArrayList<ExParam> _exParam = new FormArrayList<ExParam>(ExParam.class);
    protected String _redirectUrl;
    protected boolean _shareReport;
    protected boolean _inheritable;

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
    }

    public String getDataRegionName()
    {
        return _dataRegionName;
    }

    public void setDataRegionName(String dataRegionName)
    {
        _dataRegionName = dataRegionName;
    }

    public String getFilterParam()
    {
        return _filterParam;
    }

    public void setFilterParam(String filterParam)
    {
        _filterParam = filterParam;
    }

    public int getReportId()
    {
        return _reportId;
    }

    public void setReportId(int reportId)
    {
        _reportId = reportId;
    }

    public String getReportType()
    {
        return _reportType;
    }

    public void setReportType(String reportType)
    {
        _reportType = reportType;
    }

    public String getReportName()
    {
        return _reportName;
    }

    public void setReportName(String reportName)
    {
        _reportName = reportName;
    }

    public void addParam(String name, String value)
    {
        _exParam.add(new ExParam(name, value));
    }

    public ArrayList<ExParam> getExParam()
    {
        return _exParam;
    }

    public void setExParam(ArrayList<ExParam> param)
    {
        _exParam = param;
    }

    public String getRedirectUrl()
    {
        return _redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl)
    {
        _redirectUrl = redirectUrl;
    }

    public int getOwner()
    {
        return _owner;
    }

    public void setOwner(int owner)
    {
        _owner = owner;
    }

    public String getReportDescription()
    {
        return _reportDescription;
    }

    public void setReportDescription(String reportDescription)
    {
        _reportDescription = reportDescription;
    }

    public boolean isShareReport()
    {
        return _shareReport;
    }

    public void setShareReport(boolean shareReport)
    {
        _shareReport = shareReport;
    }

    public boolean isInheritable()
    {
        return _inheritable;
    }

    public void setInheritable(boolean inheritable)
    {
        _inheritable = inheritable;
    }

    public Report getReport() throws Exception
    {
        Report report = ReportService.get().getReport(getReportId());
        if (report == null)
            report = ReportService.get().createReportInstance(getReportType());

        if (report != null)
        {
            ReportDescriptor descriptor = report.getDescriptor();
            if (getQueryName() != null) descriptor.setProperty(ReportDescriptor.Prop.queryName, getQueryName());
            if (getSchemaName() != null) descriptor.setProperty(ReportDescriptor.Prop.schemaName, getSchemaName());
            if (getViewName() != null) descriptor.setProperty(ReportDescriptor.Prop.viewName, getViewName());
            if (getDataRegionName() != null) descriptor.setProperty(ReportDescriptor.Prop.dataRegionName, getDataRegionName());
            descriptor.setProperty(ReportDescriptor.Prop.filterParam, getFilterParam());
            if (getReportName() != null) descriptor.setReportName(getReportName());
            if (_reportDescription != null) descriptor.setReportDescription(_reportDescription);
            if (_owner != -1) descriptor.setOwner(_owner);
            if (_inheritable)
                descriptor.setFlags(descriptor.getFlags() | ReportDescriptor.FLAG_INHERITABLE);
            else
                descriptor.setFlags(descriptor.getFlags() & ~ReportDescriptor.FLAG_INHERITABLE);

            for (ExParam param : getExParam())
                descriptor.setProperty(param.getKey(), param.getValue());
        }
        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = new ArrayList();

        if (!StringUtils.isEmpty(_queryName))
            list.add(new Pair<String, String>(QueryParam.queryName.toString(), _queryName));
        if (!StringUtils.isEmpty(_schemaName))
            list.add(new Pair<String, String>(QueryParam.schemaName.toString(), _schemaName));
        if (!StringUtils.isEmpty(_viewName))
            list.add(new Pair<String, String>(QueryParam.viewName.toString(), _viewName));
        if (!StringUtils.isEmpty(_dataRegionName))
            list.add(new Pair<String, String>(QueryParam.dataRegionName.toString(), _dataRegionName));
        if (!StringUtils.isEmpty(_filterParam))
            list.add(new Pair<String, String>(ReportDescriptor.Prop.filterParam.toString(), _filterParam));
        if (!StringUtils.isEmpty(_reportType))
            list.add(new Pair<String, String>(ReportDescriptor.Prop.reportType.toString(), _reportType));
        if (!StringUtils.isEmpty(_reportName))
            list.add(new Pair<String, String>(ReportDescriptor.Prop.reportName.toString(), _reportName));
        if (_reportId != -1)
            list.add(new Pair<String, String>(ReportDescriptor.Prop.reportId.toString(), Integer.toString(_reportId)));
        if (!StringUtils.isEmpty(_redirectUrl))
            list.add(new Pair<String, String>("redirectUrl", _redirectUrl));
        if (!StringUtils.isEmpty(_reportDescription))
            list.add(new Pair<String, String>(ReportDescriptor.Prop.reportDescription.toString(), _reportDescription));
        if (_owner != -1)
            list.add(new Pair<String, String>("owner", Integer.toString(_owner)));
        if (_shareReport)
            list.add(new Pair<String, String>("shareReport", Boolean.toString(_shareReport)));
        if (_inheritable)
            list.add(new Pair<String, String>("inheritable", Boolean.toString(_inheritable)));

        int i=0;
        for (ExParam param : _exParam)
        {
            list.add(new Pair<String, String>("exParam[" + i + "].key", param.getKey()));
            list.add(new Pair<String, String>("exParam[" + i++ + "].value", param.getValue()));
        }
        return list;
    }

    public static class ExParam
    {
        String _key;
        String _value;

        public ExParam(){}
        public ExParam(String key, String value)
        {
            _key = key;
            _value = value;
        }
        public void setKey(String key){_key = key;}
        public String getKey(){return _key;}
        public void setValue(String value){_value = value;}
        public String getValue(){return _value;}
    }
}
