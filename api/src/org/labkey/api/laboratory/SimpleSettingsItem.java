package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 1:40 PM
 */
public class SimpleSettingsItem extends AbstractNavItem implements SettingsNavItem
{
    String _schema;
    String _query;
    String _category;
    String _title;
    DataProvider _provider;


    public SimpleSettingsItem(DataProvider provider, String schema, String query, String category, String title)
    {
        _schema = schema;
        _query = query;
        _category = category;
        _title = title;
        _provider = provider;
    }

    public SimpleSettingsItem(DataProvider provider, String schema, String query, String category)
    {
        _schema = schema;
        _query = query;
        _category = category;
        _title = query;
        _provider = provider;
    }

    public String getSchema()
    {
        return _schema;
    }

    public String getQuery()
    {
        return _query;
    }

    public String getName()
    {
        return _query;
    }

    public String getLabel()
    {
        return _title;
    }

    public String getCategory()
    {
        return _category;
    }

    public String getRendererName()
    {
        return "navItemRenderer";
    }

    public boolean isImportIntoWorkbooks()
    {
        return false;
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return true;
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        return c.hasPermission(u, AdminPermission.class) ? PageFlowUtil.urlProvider(LaboratoryUrls.class).getImportUrl(c, u, _schema, _query) : null;
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, _schema, _query);
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        return QueryService.get().urlFor(u, c, QueryAction.executeQuery, _schema, _query);
    }

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);

        json.put("schemaName", _schema);
        json.put("queryName", _query);

        return json;
    }

    public DataProvider getDataProvider()
    {
        return _provider;
    }
}
