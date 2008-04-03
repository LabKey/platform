package org.labkey.api.query;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.ShowRows;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.BoundMap;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;

public class QuerySettings
{
    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private String _dataRegionName;
    private ActionURL _urlSortFilter;
    private boolean _allowChooseQuery = true;
    private boolean _allowChooseView = true;
    private boolean _ignoreUserFilter;
    private int _maxRows = 100;
    private long _offset = 0;

    private ShowRows _showRows = ShowRows.DEFAULT;
    private boolean _showHiddenFieldsWhenCustomizing = false;
    private HttpServletRequest _request;

    public QuerySettings(Portal.WebPart webPart, ViewContext context)
    {
        _dataRegionName = "qwp" + webPart.getIndex();
        (new BoundMap(this)).putAll(webPart.getPropertyMap());
        _request = context.getRequest();
        init(PageFlowUtil.expandLastFilter(PageFlowUtil.expandLastFilter(context)));
    }

    public QuerySettings(ActionURL url, String dataRegionName)
    {
        _dataRegionName = dataRegionName;
        _request = HttpView.currentContext().getRequest();
        init(PageFlowUtil.expandLastFilter(PageFlowUtil.expandLastFilter(url)));
    }

    public QuerySettings(ActionURL url, String dataRegionName, String queryName)
    {
        this(url, dataRegionName);
        setQueryName(queryName);
    }


    /** if you use this method be sure to call expandLastFilter() first
     * @param url parameters for filter/sort
     */
    public void setSortFilterURL(ActionURL url)
    {
        _urlSortFilter = url;
        if (url.getParameter(param(QueryParam.showAllRows)) != null)
        {
            _showRows = ShowRows.ALL;
        }
        else if (url.getParameter(param(QueryParam.showSelected)) != null)
        {
            _showRows = ShowRows.SELECTED;
        }
    }


    /*
       SEE 4805 : delete query throws NPE in QueryControllerSpring.DeleteActionQuery

       This is a horrible hack, but it is NOT correct to use an ActionURL for form binding
       which is effectively what init() does.  It will not see POST parameters for one thing.
     */
    String _getParameter(String param)
    {
        String p = StringUtils.trimToNull(_urlSortFilter.getParameter(param));
        if (null != p || !"POST".equals(_request.getMethod()))
            return p;
        return StringUtils.trimToNull(_request.getParameter(param));
    }


    protected void init(ActionURL url)
    {
        setSortFilterURL(url);

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
        }

        if (_showRows == ShowRows.DEFAULT)
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
        String viewName = getViewName();
        try {
            if (viewName != null && viewName.startsWith(QueryView.REPORTID_PARAM))
            {
                String idParam = viewName.substring(QueryView.REPORTID_PARAM.length());
                if (NumberUtils.isNumber(idParam))
                {
                    int reportId = NumberUtils.toInt(idParam);
                    return ReportService.get().getReport(reportId);
                }
            }
        }
        catch (SQLException e)
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
        assert _showRows == ShowRows.DEFAULT : "Can't set maxRows when not paginated";
        _maxRows = maxRows;
    }

    public long getOffset()
    {
        return _offset;
    }

    public void setOffset(long offset)
    {
        assert _showRows == ShowRows.DEFAULT : "Can't set maxRows when not paginated";
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
        return _urlSortFilter.clone();
    }
}
