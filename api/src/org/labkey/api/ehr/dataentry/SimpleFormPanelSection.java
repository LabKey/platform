package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.Map;

/**
 * User: bimber
 * Date: 11/15/13
 * Time: 12:49 PM
 */
public class SimpleFormPanelSection extends SimpleFormSection
{
    public SimpleFormPanelSection(String schemaName, String queryName, String label)
    {
        super(schemaName, queryName, label, "ehr-formpanel");
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);

        Map<String, Object> formConfig = new HashMap<>();
        Map<String, Object> bindConfig = new HashMap<>();
        bindConfig.put("createRecordOnLoad", true);
        formConfig.put("bindConfig", bindConfig);
        ret.put("formConfig", formConfig);

        return ret;
    }
}
