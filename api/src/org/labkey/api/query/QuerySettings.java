/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import java.sql.SQLException;

public class QuerySettings
{
    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private String _dataRegionName;
    private ReportIdentifier _reportId;
    private boolean _allowChooseQuery = true;
    private boolean _allowChooseView = true;
    private boolean _allowCustomizeView = true;
    private boolean _ignoreUserFilter;
    private int _maxRows = 100;
    private long _offset = 0;

    private ShowRows _showRows = ShowRows.PAGINATED;
    private boolean _showHiddenFieldsWhenCustomizing = false;

    PropertyValues _filterSort = null;

    ContainerFilter _containerFilter;


    public QuerySettings(String dataRegionName)
    {
        _dataRegionName = dataRegionName;
    }

    /**
     * Init the QuerySettings using all the request parameters, from context.getPropertyValues()
     */
    public QuerySettings(ViewContext context, String dataRegionName)
    {
        _dataRegionName = dataRegionName;
        init(getPropertyValues(context));
    }


    /** Init the QuerySettings using all the request parameters, from context.getPropertyValues() */
    public QuerySettings(ViewContext context, String dataRegionName, String queryName)
    {
        _dataRegionName = dataRegionName;
        init(context);
        setQueryName(queryName);
    }


    /**
     * @param params    all parameters from URL or POST, inluding dataregion.filter parameters
     * @param dataRegionName    prefix for filter params etc
     */
    public QuerySettings(PropertyValues params, String dataRegionName)
    {
        _dataRegionName = dataRegionName;
        init(params);
    }


    private PropertyValues getPropertyValues(ViewContext context)
    {
        PropertyValues pvs = context.getBindPropertyValues();
        if (null == pvs)
        {
            //noinspection ThrowableInstanceNeverThrown
            Logger.getInstance(QuerySettings.class).warn("PropertyValues not set");
            //throw new IllegalStateException("PropertyValues not set");'
            pvs = context.getActionURL().getPropertyValues();
        }
        return pvs;
    }



    /**
     * @param url parameters for filter/sort
     */
    public void setSortFilterURL(ActionURL url)
    {
        setSortFilter(url.getPropertyValues());
    }


    public void setSortFilter(PropertyValues pvs)
    {
        _filterSort = pvs;
        String showRowsParam = _getParameter(param(QueryParam.showRows));
        if (showRowsParam != null)
        {
            try
            {
                _showRows = ShowRows.valueOf(showRowsParam.toUpperCase());
            }
            catch (IllegalArgumentException ex)
            {
                _showRows = ShowRows.PAGINATED;
            }
        }
    }


    protected String _getParameter(String param)
    {
        PropertyValue pv = _filterSort.getPropertyValue(param);
        if (pv == null)
            return null;
        Object v = pv.getValue();
        if (v.getClass().isArray())
        {
            Object[] a = (Object[])v;
            v = a.length == 0 ? null : a[0];
        }
        return v == null ? null : String.valueOf(v);
    }

    public void init(ViewContext context)
    {
        init(getPropertyValues(context));    
    }


    /**
     * Initialize QuerySettings from the PropertyValues, binds all fields that are supported on the URL
     *. such as viewName.  Use setSortFilter() to provide sort filter parameters w/o affecting the other
     * properties.
     */
    public void init(PropertyValues pvs)
    {
        if (null == pvs)
            pvs = new MutablePropertyValues();
        setSortFilter(pvs);

        if (getAllowChooseQuery())
        {
            String param = param(QueryParam.queryName);
            String queryName = StringUtils.trimToNull(_getParameter(param));
            if (queryName != null)
            {
                setQueryName(queryName);
            }
        }
        if (getAllowChooseView())
        {
            String viewName = StringUtils.trimToNull(_getParameter(param(QueryParam.viewName)));
            if (viewName != null)
            {
                setViewName(viewName);
            }
            if (_getParameter(param(QueryParam.ignoreFilter)) != null)
            {
                _ignoreUserFilter = true;
            }

            setReportId(ReportService.get().getReportIdentifier(_getParameter(param(QueryParam.reportId))));
        }

        if (_showRows == ShowRows.PAGINATED)
        {
            String offsetParam = _getParameter(param(QueryParam.offset));
            if (offsetParam != null)
            {
                try
                {
                    long offset = Long.parseLong(offsetParam);
                    if (offset > 0)
                        _offset = offset;
                }
                catch (NumberFormatException e) { }
            }

            String maxRowsParam = _getParameter(param(QueryParam.maxRows));
            if (maxRowsParam != null)
            {
                try
                {
                    int maxRows = Integer.parseInt(maxRowsParam);
                    if (maxRows > 0)
                        _maxRows = maxRows;
                }
                catch (NumberFormatException e) { }
            }

            String containerFilterNameParam = _getParameter(param(QueryParam.containerFilterName));
            if (containerFilterNameParam != null)
                setContainerFilterName(containerFilterNameParam);
        }
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public ReportIdentifier getReportId()
    {
        return _reportId;
    }

    public void setReportId(ReportIdentifier reportId)
    {
        _reportId = reportId;
    }

    public void setDataRegionName(String name)
    {
        _dataRegionName = name;
    }

    public String getDataRegionName()
    {
        return _dataRegionName;
    }

    public String getSelectionKey()
    {
        return DataRegionSelection.getSelectionKey(getSchemaName(), getQueryName(), getViewName(), getDataRegionName());
    }

    public void setAllowChooseQuery(boolean b)
    {
        _allowChooseQuery = b;
    }

    public boolean getAllowChooseQuery()
    {
        return _allowChooseQuery;
    }

    public void setAllowChooseView(boolean b)
    {
        _allowChooseView = b;
    }

    public boolean getAllowChooseView()
    {
        return _allowChooseView;
    }

    public String param(QueryParam param)
    {
        switch (param)
        {
            case schemaName:
                return param.toString();
            default:
                return param(param.toString());
        }
    }

    protected String param(String param)
    {
        if (getDataRegionName() == null)
            return param;
        return getDataRegionName() + "." + param;
    }

    public QueryDefinition getQueryDef(UserSchema schema)
    {
        String queryName = getQueryName();
        if (queryName == null)
            return null;
        QueryDefinition ret = QueryService.get().getQueryDef(schema.getContainer(), schema.getSchemaName(), queryName);
        if (ret != null)
        {
            return ret;
        }
        return schema.getQueryDefForTable(queryName);
    }

    public CustomView getCustomView(ViewContext context, QueryDefinition queryDef)
    {
        if (queryDef == null)
        {
            return null;
        }
        return queryDef.getCustomView(context.getUser(), context.getRequest(), getViewName());
    }

    public Report getReportView(ViewContext context)
    {
        try {
            if (getReportId() != null)
                return getReportId().getReport();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean getIgnoreUserFilter()
    {
        return _ignoreUserFilter;
    }

    public void setIgnoreUserFilter(boolean b)
    {
        _ignoreUserFilter = b;
    }

    public int getMaxRows()
    {
        return _maxRows;
    }

    public void setMaxRows(int maxRows)
    {
        assert _showRows == ShowRows.PAGINATED : "Can't set maxRows when not paginated";
        _maxRows = maxRows;
    }

    public long getOffset()
    {
        return _offset;
    }

    public void setOffset(long offset)
    {
        assert _showRows == ShowRows.PAGINATED : "Can't set maxRows when not paginated";
        _offset = offset;
    }

    public ShowRows getShowRows()
    {
        return _showRows;
    }

    public void setShowRows(ShowRows showRows)
    {
        _showRows = showRows;
    }

    public boolean isShowHiddenFieldsWhenCustomizing()
    {
        return _showHiddenFieldsWhenCustomizing;
    }

    public void setShowHiddenFieldsWhenCustomizing(boolean showHiddenFieldsWhenCustomizing)
    {
        _showHiddenFieldsWhenCustomizing = showHiddenFieldsWhenCustomizing;
    }

    public ActionURL getSortFilterURL()
    {
        ActionURL url = HttpView.getRootContext().cloneActionURL();
        url.deleteParameters();
        url.setPropertyValues(_filterSort);
        return url;
    }

    public boolean isAllowCustomizeView()
    {
        return _allowCustomizeView;
    }

    public void setAllowCustomizeView(boolean allowCustomizeView)
    {
        _allowCustomizeView = allowCustomizeView;
    }

    ContainerFilter getContainerFilter()
    {
        return _containerFilter;
    }

    public void setContainerFilterName(String name)
    {
        _containerFilter = ContainerFilter.Filters.valueOf(name);
    }
}
