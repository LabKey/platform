package org.labkey.api.audit.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Sep 19, 2007
 */
public abstract class AuditLogQueryView extends QueryView
{
    protected List<String> _columns = new ArrayList();
    protected List<DisplayColumn> _displayColumns = new ArrayList();
    protected SimpleFilter _filter;
    protected Sort _sort;
    protected String _title;
    protected Map<String, AuditDisplayColumnFactory> _displayColFactory = new HashMap();
    protected boolean _showCustomizeLink;

    public AuditLogQueryView(UserSchema schema, QuerySettings settings, SimpleFilter filter)
    {
        super(schema, settings);
        _filter = filter;
        _buttonBarPosition = DataRegion.ButtonBarPosition.NONE;
    }

    public AuditLogQueryView(ViewContext context)
    {
        super((UserSchema)null);

        _buttonBarPosition = DataRegion.ButtonBarPosition.NONE;
        UserSchema schema = AuditLogService.get().createSchema(context.getUser(), context.getContainer());
        String tableName = AuditLogService.get().getTableName();
        QuerySettings settings = new QuerySettings(context.getActionURL(), tableName);
        settings.setSchemaName(schema.getSchemaName());
        settings.setQueryName(tableName);

        setSchema(schema);
        setSettings(settings);
    }

    public void setVisibleColumns(String[] columnNames)
    {
        for (String name : columnNames)
            _columns.add(name.toLowerCase());
    }

    public void setFilter(SimpleFilter filter)
    {
        _filter = filter;
    }
    
    public void setSort(Sort sort)
    {
        _sort = sort;
    }

    public void addDisplayColumn(DisplayColumn dc)
    {
        _displayColumns.add(dc);
    }

    public abstract void addDisplayColumn(int index, DisplayColumn dc);

    /**
     * @deprecated
     */
    public void setDisplayColumnFactory(String name, AuditDisplayColumnFactory factory)
    {
        //_columns.add(name.toLowerCase());
        _displayColFactory.put(name, factory);
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public void setShowCustomizeLink(boolean showCustomizeLink)
    {
        _showCustomizeLink = showCustomizeLink;
    }
}

