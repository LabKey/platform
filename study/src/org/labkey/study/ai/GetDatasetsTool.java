package org.labkey.study.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ai.ClaudeTool;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

public class GetDatasetsTool implements ClaudeTool
{
    @Override
    public String getName()
    {
        return "get_datasets";
    }

    @Override
    public String getDescription()
    {
        return "Returns the list of available study datasets with their Label, Name, and Description.";
    }

    @Override
    public JSONObject getInputSchema()
    {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("properties", new JSONObject());
        return schema;
    }

    @Override
    public JSONArray execute(User user, Container container, JSONObject input)
    {
        JSONArray resultArray = new JSONArray();
        UserSchema studySchema = QueryService.get().getUserSchema(user, container, "study");
        if (studySchema != null)
        {
            TableInfo datasetsTable = studySchema.getTable("Datasets");
            if (datasetsTable != null)
            {
                TableSelector ts = new TableSelector(datasetsTable, PageFlowUtil.set("Label", "Name", "Description"), null, null);
                ts.forEach(rs -> {
                    JSONObject ds = new JSONObject();
                    ds.put("Label", rs.getString("Label"));
                    ds.put("Name", rs.getString("Name"));
                    ds.put("Description", rs.getString("Description"));
                    resultArray.put(ds);
                });
            }
        }
        return resultArray;
    }
}
