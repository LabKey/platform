package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 9:33 AM
 */
abstract public class AbstractNavItem implements NavItem
{
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = new JSONObject();
        ret.put("name", getName());
        ret.put("label", getLabel());
        ret.put("category", getCategory());
        ret.put("importIntoWorkbooks", isImportIntoWorkbooks());

        ret.put("importUrl", getUrlObject(getImportUrl(c, u)));
        ret.put("searchUrl", getUrlObject(getSearchUrl(c, u)));
        ret.put("browseUrl", getUrlObject(getBrowseUrl(c, u)));
        ret.put("prepareExptUrl", getUrlObject(getPrepareExptUrl(c, u)));

        return ret;
    }

    protected JSONObject getUrlObject(ActionURL url)
    {
        JSONObject json = new JSONObject();
        if (url != null)
        {
            json.put("url", url);
            json.put("controller", url.getController());
            json.put("action", url.getAction());
            json.put("params", url.getParameterMap());
        }
        return json;
    }

    protected String getKey()
    {
        return this.getClass().getName() + "|" + getName();
    }
}
