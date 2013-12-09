package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: bimber
 * Date: 6/9/13
 * Time: 4:15 PM
 */
public class SingleQueryFormSection extends SimpleFormSection
{
    public SingleQueryFormSection(String schema, String query, String label)
    {
        super(schema, query, label, "ehr-formpanel");
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);

        Map<String, Object> formConfig = new HashMap<>();
        Map<String, Object> bindConfig = new HashMap<>();
        bindConfig.put("createRecordOnLoad", true);
        formConfig.put("bindConfig", bindConfig);
        formConfig.put("title", null);
        formConfig.put("label", null);
        formConfig.put("maxItemsPerCol", 8);
        ret.put("formConfig", formConfig);

        return ret;
    }
}
