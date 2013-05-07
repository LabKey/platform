package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/14/13
 * Time: 9:31 AM
 */
public class JSTabbedReportItem extends TabbedReportItem
{
    private String _jsHandler;

    public JSTabbedReportItem(DataProvider provider, String name, String label, String category, String jsHandler)
    {
        super(provider, name, label, category);
        _reportType = "js";
        _jsHandler = jsHandler;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);
        json.put("jsHandler", _jsHandler);
        return json;
    }
}
