/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.actions.ReportForm;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.util.Pair;

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
    protected ReportIdentifier _reportId;
    protected int _owner = -1;
    protected String _reportType;
    protected String _reportName;
    protected String _reportDescription;
    protected String _redirectUrl;
    protected boolean _shareReport;
    protected boolean _inheritable;
    protected boolean _cached;

    public ReportDesignBean(){}
    public ReportDesignBean(QuerySettings settings)
    {
        setSchemaName(settings.getSchemaName());
        setQueryName(settings.getQueryName());
        setViewName(settings.getViewName());
        setDataRegionName(settings.getDataRegionName());
    }
    
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

    public ReportIdentifier getReportId()
    {
        return _reportId;
    }

    public void setReportId(ReportIdentifier reportId)
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

    public boolean isCached()
    {
        return _cached;
    }

    public void setCached(boolean cached)
    {
        _cached = cached;
    }

    public Report getReport() throws Exception
    {
        Report report = null;
        if(null != getReportId())
            report = getReportId().getReport();
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
            descriptor.setProperty(ReportDescriptor.Prop.cached, _cached);
            if (null != getContainer())
                descriptor.setContainer(getContainer().getId());
        }
        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = new ArrayList<Pair<String, String>>();

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
        if (null != _reportId)
            list.add(new Pair<String, String>(ReportDescriptor.Prop.reportId.toString(), _reportId.toString()));
        if (!StringUtils.isEmpty(_redirectUrl))
            list.add(new Pair<String, String>("redirectUrl", _redirectUrl));
        if (!StringUtils.isEmpty(_reportDescription))
            list.add(new Pair<String, String>(ReportDescriptor.Prop.reportDescription.toString(), _reportDescription));
        if (_owner != -1)
            list.add(new Pair<String, String>("owner", Integer.toString(_owner)));
        if (_shareReport)
            list.add(new Pair<String, String>("shareReport", String.valueOf(_shareReport)));
        if (_inheritable)
            list.add(new Pair<String, String>("inheritable", String.valueOf(_inheritable)));
        if (_cached)
            list.add(new Pair<String, String>(ReportDescriptor.Prop.cached.name(), String.valueOf(_cached)));

        return list;
    }
}
