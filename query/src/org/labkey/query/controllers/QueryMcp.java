package org.labkey.query.controllers;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.labkey.api.module.McpProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Arrays;
import java.util.List;

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
}

