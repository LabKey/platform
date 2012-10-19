package org.labkey.api.laboratory;

import org.labkey.api.data.Container;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/8/12
 * Time: 10:09 PM
 */
public class UrlNavItem extends AbstractNavItem
{
    private DataProvider _provider;
    private DetailsURL _url;
    private String _label;
    private String _category;

    public UrlNavItem(DataProvider provider, DetailsURL url, String label, String category)
    {
        _provider = provider;
        _url = url;
        _label = label;
        _category = category;
    }

    public String getName()
    {
        return _label;
    }

    public String getLabel()
    {
        return _label;
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
        return null;
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return null;
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        return _url.copy(c).getActionURL();
    }

    public DataProvider getDataProvider()
    {
        return _provider;
    }
}
