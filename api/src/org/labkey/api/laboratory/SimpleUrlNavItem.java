package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/4/13
 * Time: 9:39 AM
 */
public class SimpleUrlNavItem extends AbstractNavItem
{
    protected DataProvider _provider;
    protected String _label;
    protected String _itemText;
    protected DetailsURL _itemUrl = null;
    protected String _urlString = null;

    protected String _category;

    public SimpleUrlNavItem(DataProvider provider, String label, String itemText, DetailsURL itemUrl, String category)
    {
        _provider = provider;
        _label = label;
        _itemText = itemText;
        _itemUrl = itemUrl;
        _category = category;
    }

    public SimpleUrlNavItem(DataProvider provider, String label, String itemText, String urlString, String category)
    {
        _provider = provider;
        _label = label;
        _itemText = itemText;
        _urlString = urlString;
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
        return "defaultRenderer";
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return true;
    }

    protected ActionURL getActionURL(Container c, User u)
    {
        if (_itemUrl != null)
            return _itemUrl.copy(c).getActionURL();

        return null;
    }

    public DataProvider getDataProvider()
    {
        return _provider;
    }

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);
        ret.put("itemText", _itemText);

        ActionURL url = getActionURL(c, u);
        if (url != null)
            ret.put("urlConfig", getUrlObject(url));
        else
            ret.put("url", _urlString);

        return ret;
    }
}
