/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.actions.ReportForm;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.List;

/**
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
    protected int _owner = -1;
    protected String _reportType;
    protected String _reportName;
    protected String _reportDescription;
    protected String _redirectUrl;
    protected String _reportAccess;
    protected boolean _shareReport;
    protected boolean _inheritable;
    protected boolean _cached;

    public ReportDesignBean()
    {
    }

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

    public String getReportAccess()
    {
        return _reportAccess;
    }

    public void setReportAccess(String reportAccess)
    {
        _reportAccess = reportAccess;
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

    public Report getReport(ContainerUser cu) throws Exception
    {
        Report report = null;
        if (null != getReportId())
            report = getReportId().getReport(cu);
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

            // need to guarantee a container id on the report descriptor else we will blow up when we try to get the security policy
            if ((null == descriptor.getContainerId()) && (null != getContainer()))
                descriptor.setContainer(getContainer().getId());
        }

        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = new ArrayList<>();

        if (!StringUtils.isEmpty(_queryName))
            list.add(new Pair<>(QueryParam.queryName.toString(), _queryName));
        if (!StringUtils.isEmpty(_schemaName))
            list.add(new Pair<>(QueryParam.schemaName.toString(), _schemaName));
        if (!StringUtils.isEmpty(_viewName))
            list.add(new Pair<>(QueryParam.viewName.toString(), _viewName));
        if (!StringUtils.isEmpty(_dataRegionName))
            list.add(new Pair<>(QueryParam.dataRegionName.toString(), _dataRegionName));
        if (!StringUtils.isEmpty(_filterParam))
            list.add(new Pair<>(ReportDescriptor.Prop.filterParam.toString(), _filterParam));
        if (!StringUtils.isEmpty(_reportType))
            list.add(new Pair<>(ReportDescriptor.Prop.reportType.toString(), _reportType));
        if (!StringUtils.isEmpty(_reportName))
            list.add(new Pair<>(ReportDescriptor.Prop.reportName.toString(), _reportName));
        if (null != _reportId)
            list.add(new Pair<>(ReportDescriptor.Prop.reportId.toString(), _reportId.toString()));
        if (!StringUtils.isEmpty(_redirectUrl))
            list.add(new Pair<>(ReportDescriptor.Prop.redirectUrl.name(), _redirectUrl));
        if (!StringUtils.isEmpty(_reportDescription))
            list.add(new Pair<>(ReportDescriptor.Prop.reportDescription.toString(), _reportDescription));
        if (_owner != -1)
            list.add(new Pair<>("owner", Integer.toString(_owner)));
        if (!StringUtils.isEmpty(_reportAccess))
            list.add(new Pair<>("reportAccess", _reportAccess));
        if (_shareReport)
            list.add(new Pair<>("shareReport", String.valueOf(_shareReport)));
        if (_inheritable)
            list.add(new Pair<>("inheritable", String.valueOf(_inheritable)));
        if (_cached)
            list.add(new Pair<>(ReportDescriptor.Prop.cached.name(), String.valueOf(_cached)));

        return list;
    }

    void populateFromDescriptor(ReportDescriptor descriptor)
    {
        setQueryName(descriptor.getProperty(ReportDescriptor.Prop.queryName));
        setSchemaName(descriptor.getProperty(ReportDescriptor.Prop.schemaName));
        setViewName(descriptor.getProperty(ReportDescriptor.Prop.viewName));
        setDataRegionName(descriptor.getProperty(ReportDescriptor.Prop.dataRegionName));
        setReportName(descriptor.getProperty(ReportDescriptor.Prop.reportName));
        setReportType(descriptor.getProperty(ReportDescriptor.Prop.reportType));
        setFilterParam(descriptor.getProperty(ReportDescriptor.Prop.filterParam));
        setCached(BooleanUtils.toBoolean(descriptor.getProperty(ReportDescriptor.Prop.cached)));

        setReportAccess(descriptor.getAccess());
        setShareReport((descriptor.isShared()));
        setInheritable((descriptor.getFlags() & ReportDescriptor.FLAG_INHERITABLE) != 0);
        setRedirectUrl(getViewContext().getActionURL().getParameter(ReportDescriptor.Prop.redirectUrl.name()));
    }
}
