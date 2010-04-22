/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.Aggregate;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.util.HString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import java.net.URISyntaxException;
import java.util.List;
import java.util.ArrayList;

public class QuerySettings
{
    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private String _dataRegionName;
    private List<FieldKey> _fieldKeys;
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
    private URLHelper _returnURL = null;

    private String _containerFilterName;
    private List<Aggregate> _aggregates = new ArrayList<Aggregate>();


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

    public void setAggregates(PropertyValues pvs)
    {
        for(PropertyValue val : pvs.getPropertyValues())
        {
            if(val.getName().startsWith(getDataRegionName() + Aggregate.QS_PREFIX))
            {
                String columnName = val.getName().substring((getDataRegionName() + Aggregate.QS_PREFIX).length());
                Aggregate.Type type;
                try
                {
                    type = Aggregate.Type.valueOf(((String)val.getValue()).toUpperCase());
                }
                catch(IllegalArgumentException e)
                {
                    throw new IllegalArgumentException("'" + val.getValue() + "' is not a valid aggregate type.");
                }
                _aggregates.add(new Aggregate(columnName, type));
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
        setAggregates(pvs);

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

        String returnURL = _getParameter(ReturnUrlForm.Params.returnUrl.toString());
        if (returnURL == null)
            returnURL = _getParameter("returnURL");
        if (returnURL == null)
            returnURL = _getParameter(QueryParam.srcURL.toString());
        if (returnURL != null)
        {
            try
            {
                setReturnURL(new URLHelper(returnURL));
            }
            catch (URISyntaxException _) { }
        }

        String columns = StringUtils.trimToNull(_getParameter(param(QueryParam.columns)));
        if (null != columns)
        {
            String[] colArray = columns.split(",");
            _fieldKeys = new ArrayList<FieldKey>();
            for (String key : colArray)
            {
                if (!(StringUtils.isEmpty(key)))
                {
                    _fieldKeys.add(FieldKey.fromString(StringUtils.trim(key)));

                }
            }
        }
    }

	public void setSchemaName(HString schemaName)
	{
		_schemaName = schemaName.toString();
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

    /**
     * Returns the "returnURL" parameter or null if none.
     * The url may not necessarily be an ActionURL, e.g. if served from a FileContent html page.
     */
    public URLHelper getReturnURL()
    {
        return _returnURL;
    }

    public void setReturnURL(URLHelper returnURL)
    {
        _returnURL = returnURL;
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
        if (ret != null && getContainerFilterName() != null)
            ret.setContainerFilter(ContainerFilter.getContainerFilterByName(getContainerFilterName(), schema.getUser()));
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

    public Report getReportView()
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

    public String getContainerFilterName()
    {
        return _containerFilterName;
    }

    public void setContainerFilterName(String name)
    {
        _containerFilterName = name;
    }

    public List<Aggregate> getAggregates()
    {
        return _aggregates;
    }

    public void setAggregates(List<Aggregate> aggregates)
    {
        _aggregates = aggregates;
    }

    public List<FieldKey> getFieldKeys()
    {
        return _fieldKeys;
    }

    public void setFieldKeys(List<FieldKey> keys)
    {
        _fieldKeys = keys;
    }
}
