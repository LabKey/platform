package org.labkey.query.controllers;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.module.McpProvider;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/* TODO: integrate ToolContext support */

public class QueryMcp implements McpProvider
{
    @Override
    public List<ToolCallback> getMcpTools()
    {
        ToolCallback[] queryTools = ToolCallbacks.from(this);
        return Arrays.asList(queryTools);
    }

    @Override
    public List<McpServerFeatures.SyncPromptSpecification> getMcpPrompts()
    {
        return List.of();
    }

    @Override
    public List<McpServerFeatures.SyncResourceSpecification> getMcpResources()
    {
        return List.of();
    }

    @Tool(description = "Provide column metadata for a sql table.  This tool will also return SQL source for saved queries.")
    String listColumnMetaData(@ToolParam(description = "Fully qualified table name as it would appear in SQL e.g. \"schema\".\"table\"") String fullQuotedTableName)
    {
        var json = QueryController.listColumnsForTable(fullQuotedTableName);
        // can I just return a JSONObject
        return json.toString();
    }

    @Tool(description = "Provide list of tables within the provided schema.")
    String listTablesForSchema(@ToolParam(description = "Fully qualified schema name as it would appear in SQL e.g. \"schema\"") String quotedSchemaName)
    {
        var json = QueryController.listTablesForSchema(quotedSchemaName);
        // can I just return a JSONObject
        return json.toString();
    }

    @Tool(description = "Provide list of database schemas")
    String listSchemas()
    {
        ViewContext context = HttpView.currentView().getViewContext();
        var map = QueryController.listAllSchemas(DefaultSchema.get(context.getUser(), context.getContainer()));
        var array = new JSONArray();
        for (var entry : map.entrySet())
        {
                array.put(new JSONObject(Map.of(
                        "name", entry.getKey().getName(),
                        "quotedName", entry.getKey().toSQLString(),
                        "description", StringUtils.trimToEmpty(entry.getValue().getDescription())
                )));
        }
        return new JSONObject(Map.of("success", "true", "schemas", array)).toString();
    }


    @Tool(description = "Provide the SQL source for a saved query.")
    String getSourceForSaveQuery(@ToolParam(description = "Fully qualified query name as it would appear in SQL e.g. \"schema\".\"saved query\"") String fullQuotedTableName)
    {
        var json = QueryController.listTablesForSchema(fullQuotedTableName);
        if (json.has("sql"))
            return "```sql\n" + json.getString("sql") + "\n```\n";
        else
            return "I could not find the source for " + fullQuotedTableName;
    }
}

