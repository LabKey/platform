package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/21/12
 * Time: 5:52 PM
 */
public class SingleNavItem extends AbstractNavItem
{
    protected DataProvider _provider;
    protected String _label;
    protected String _itemText;
    protected DetailsURL _itemUrl;

    protected String _category;

    public SingleNavItem(DataProvider provider, String label, String itemText, DetailsURL itemUrl, String category)
    {
        _provider = provider;
        _label = label;
        _itemText = itemText;
        _itemUrl = itemUrl;
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
        return "singleItemRenderer";
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return true;
    }

    protected ActionURL getItemUrl(Container c, User u)
    {
        return _itemUrl == null ? null : _itemUrl.copy(c).getActionURL();
    }

    public DataProvider getDataProvider()
    {
        return _provider;
    }

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);
        ret.put("itemText", _itemText);
        ret.put("itemUrl", getUrlObject(getItemUrl(c, u)));

        return ret;
    }
}
