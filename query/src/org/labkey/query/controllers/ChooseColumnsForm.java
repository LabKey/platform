package org.labkey.query.controllers;

import org.apache.commons.lang.StringUtils;
import org.apache.struts.action.ActionMapping;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ActionURL;
import org.labkey.query.CustomViewImpl;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;

public class ChooseColumnsForm extends DesignForm
{
    public LinkedHashSet<FieldKey> ff_selectedColumns = new LinkedHashSet();
    private String _dataRegionName;
    public String ff_columnListName;
    public boolean ff_saveForAllUsers;
    public boolean ff_saveFilter;
    public boolean ff_inheritable;

    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        super.reset(actionMapping, request);
        if (null == getQuerySettings())
            return;
        _dataRegionName = request.getParameter(QueryParam.dataRegionName.toString());
        ff_columnListName = getQuerySettings().getViewName();
        CustomView cv = getCustomView();
        if (cv != null && cv.getColumns() != null)
        {
            ff_selectedColumns.addAll(cv.getColumns());
            ff_inheritable = cv.canInherit();
        }
        if (ff_selectedColumns.isEmpty())
        {
            TableInfo table = getQueryDef().getTable(null, getSchema(), null);
            if (table != null)
            {
                for (ColumnInfo column : table.getColumns())
                {
                    if (!column.isHidden() && !CustomViewImpl.isUnselectable(column))
                    {
                        ff_selectedColumns.add(new FieldKey(null, column.getName()));
                    }
                }
            }
        }
    }

    public void setFf_selectedColumns(String columns)
    {
        ff_selectedColumns = new LinkedHashSet();
        if (columns != null)
        {
            for (String column : StringUtils.split(columns, '&'))
            {
                ff_selectedColumns.add(FieldKey.fromString(column));
            }
        }
    }

    public void setFf_saveForAllUsers(boolean b)
    {
        ff_saveForAllUsers = b;
    }

    public void setFf_inheritable(boolean ff_inheritable)
    {
        this.ff_inheritable = ff_inheritable;
    }

    public boolean isCustomViewInherited()
    {
        CustomView customView = getCustomView();
        if (customView != null && customView.canInherit())
        {
            return !getContainer().getId().equals(customView.getContainer().getId());
        }
        return false;
    }

    public void setFf_columnListName(String name)
    {
        ff_columnListName = name;
    }

    private void addColumns(Map<FieldKey, ColumnInfo> map, TableInfo table, FieldKey tableKey, int depth)
    {
        for (String columnName : table.getColumnNameSet())
        {
            ColumnInfo column = table.getColumn(columnName);
            FieldKey fieldKey;
            if (tableKey == null)
            {
                fieldKey = FieldKey.fromString(columnName);
            }
            else
            {
                fieldKey = new FieldKey(tableKey, columnName);
            }
            map.put(fieldKey, column);
            if (depth > 0)
            {
                TableInfo fkTableInfo = column.getFkTableInfo();
                if (fkTableInfo != null)
                {
                    addColumns(map, fkTableInfo, fieldKey, depth - 1);
                }
            }
        }
    }

    protected QuerySettings createQuerySettings(UserSchema schema)
    {
        String srcURL = getRequest().getParameter(QueryParam.srcURL.toString());
        if (null == srcURL)
            return null;
        QuerySettings ret = schema.getSettings(new ActionURL(srcURL), getRequest().getParameter(QueryParam.dataRegionName.toString()));
        ret.setQueryName(getRequest().getParameter(QueryParam.queryName.toString()));
        ret.setViewName(getRequest().getParameter(QueryParam.viewName.toString()));
        return ret;
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = super.urlFor(action);
        ret.addParameter(QueryParam.srcURL.toString(), getSourceURL().toString());
        ret.addParameter(QueryParam.dataRegionName.toString(), _dataRegionName);
        ret.addParameter(QueryParam.queryName.toString(), getQuerySettings().getQueryName());
        return ret;
    }

    public Map<FieldKey, ColumnInfo> getAvailableColumns()
    {
        Map<FieldKey, ColumnInfo> ret = new TreeMap();
        TableInfo table = getQueryDef().getTable(null, getSchema(), null);
        addColumns(ret, table, null, 3);
        return ret;
    }

    public String getDataRegionName()
    {
        return _dataRegionName;
    }

    protected boolean isFilterOrSort(String dataRegionName, String param)
    {
        assert param.startsWith(dataRegionName + ".");
        String check = param.substring(dataRegionName.length() + 1);
        if (check.indexOf("~") >= 0)
            return true;
        if ("sort".equals(check))
            return true;
        return false;
    }

    public boolean hasFilterOrSort()
    {
        CustomView current = getCustomView();
        ActionURL url = getSourceURL();

        if (current != null && current.hasFilterOrSort())
        {
            if (url.getParameter(getDataRegionName() + "." + QueryParam.ignoreFilter.toString()) != null)
                return true; 
        }
        for (String key : url.getKeysByPrefix(getDataRegionName() + "."))
        {
            if (isFilterOrSort(getDataRegionName(), key))
                return true;
        }
        return false;
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        ActionURL src = getSourceURL();
        if (src.getParameter(_dataRegionName + "." + QueryParam.ignoreFilter.toString()) == null)
        {
            CustomView current = getCustomView();
            if (current != null)
            {
                current.applyFilterAndSortToURL(url, dataRegionName);
            }
        }
        for (String key : src.getKeysByPrefix(_dataRegionName + "."))
        {
            if (!isFilterOrSort(getDataRegionName(), key))
                continue;
            String newKey = dataRegionName + key.substring(_dataRegionName.length());
            for (String value : src.getParameters(key))
            {
                url.addParameter(newKey, value);
            }
        }
    }
    
    public ActionURL getSourceURL()
    {
        String value = getRequest().getParameter(QueryParam.srcURL.toString());
        if (value == null)
            return null;
        return new ActionURL(value);
    }

    public void setFf_saveFilter(boolean b)
    {
        ff_saveFilter = b;
    }

    public boolean canSaveForAllUsers()
    {
        return getContainer().hasPermission(getUser(), ACL.PERM_ADMIN);
    }

    public boolean canEdit()
    {
        return !isCustomViewInherited();
    }
}
