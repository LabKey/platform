package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/21/12
 * Time: 5:08 PM
 */
abstract public class AbstractImportingNavItem extends AbstractNavItem implements ImportingNavItem
{
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);

        ret.put("importIntoWorkbooks", isImportIntoWorkbooks());

        ret.put("importUrl", getUrlObject(getImportUrl(c, u)));
        ret.put("searchUrl", getUrlObject(getSearchUrl(c, u)));
        ret.put("browseUrl", getUrlObject(getBrowseUrl(c, u)));

        return ret;
    }

    public String getRendererName()
    {
        return "importingItemRenderer";
    }
}
