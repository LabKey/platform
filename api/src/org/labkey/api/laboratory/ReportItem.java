package org.labkey.api.laboratory;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 5/5/13
 * Time: 9:41 AM
 */
public class ReportItem extends SimpleQueryNavItem
{
    public ReportItem(DataProvider provider, String schema, String query, String category, String label)
    {
        super(provider, schema, query, category, label);
    }

    public ReportItem(DataProvider provider, String schema, String query, String category)
    {
        super(provider, schema, query, category);
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);
        TabbedReportItem.applyOverrides(this, c, json);

        return json;
    }
}
