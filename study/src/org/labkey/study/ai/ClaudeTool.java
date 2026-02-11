package org.labkey.study.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

public interface ClaudeTool
{
    /** Tool name sent to the Claude API (e.g. "get_datasets") */
    String getName();

    /** Human-readable description for the Claude API */
    String getDescription();

    /** Build the input_schema JSONObject */
    JSONObject getInputSchema();

    /** Convenience: build the full tool definition JSON */
    default JSONObject getToolDefinition()
    {
        JSONObject tool = new JSONObject();
        tool.put("name", getName());
        tool.put("description", getDescription());
        tool.put("input_schema", getInputSchema());
        return tool;
    }

    /** Execute the tool and return the result as a JSONArray */
    JSONArray execute(User user, Container container, JSONObject input);
}
