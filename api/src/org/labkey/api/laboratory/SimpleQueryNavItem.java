package org.labkey.api.laboratory;

import org.labkey.api.data.Container;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 12:27 PM
 */
public class SimpleQueryNavItem extends AbstractNavItem
{
    String _schema;
    String _query;
    String _category;

    public SimpleQueryNavItem(String schema, String query, String category)
    {
        _schema = schema;
        _query = query;
        _category = category;
    }

    public String getName()
    {
        return _query;
    }

    public String getLabel()
    {
        return _query;
    }

    public String getCategory()
    {
        return _category;
    }

    public boolean isImportIntoWorkbooks()
    {
        return true;
    }

    public boolean isVisible(Container c, User u)
    {
        return true;
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        return QueryService.get().urlFor(u, c, QueryAction.importData, _schema, _query);
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, _schema, _query);
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        return QueryService.get().urlFor(u, c, QueryAction.executeQuery, _schema, _query);
    }

    public ActionURL getPrepareExptUrl(Container c, User u)
    {
        return null;
    }
}
