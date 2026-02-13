package org.labkey.query.controllers;

import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableDescription;
import org.labkey.api.data.TableInfo;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleSchemaTreeVisitor;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.query.sql.SqlParser;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class QueryMcp implements McpService.McpImpl
{
    private static final Logger LOG = LogHelper.getLogger(QueryMcp.class, "MCP query tools");
    @McpResource(
            uri = "resource://org/labkey/query/controllers/LabKeySql.md",
            mimeType = "application/markdown",
            name = "LabKey SQL",
            description = "Provide documentation for LabKey SQL specific syntax")
    public McpSchema.ReadResourceResult getLabKeySQLDocumentation() throws IOException
    {
        String markdown = IOUtils.resourceToString("org/labkey/query/controllers/LabKeySql.md", null, QueryController.class.getClassLoader());
        return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(
                        "resource://org/labkey/query/controllers/LabKeySql.md",
                        "application/markdown",
                        markdown)
        ));
    }


    private static String checkReadPermission(McpContext context)
    {
        Container container = context.getContainer();
        if (!container.hasPermission(context.getUser(), ReadPermission.class))
            return new JSONObject(Map.of("success", Boolean.FALSE, "error", "You don't have read access to the folder: " + container.getPath())).toString();
        return null;
    }

    @Tool(description = "Provide column metadata for a sql table.  This tool will also return SQL source for saved queries.")
    String listColumnMetaData(@ToolParam(description = "Fully qualified table name as it would appear in SQL e.g. \"schema\".\"table\"") String fullQuotedTableName, ToolContext toolContext)
    {
        McpContext context = getContext(toolContext);
        String permError = checkReadPermission(context);
        if (null != permError)
            return permError;
        var json = _listColumnsForTable(context, fullQuotedTableName);
        return json.toString();
    }

    @Tool(description = "Provide list of tables within the provided schema.")
    String listTablesForSchema(@ToolParam(description = "Fully qualified schema name as it would appear in SQL e.g. \"schema\"") String quotedSchemaName, ToolContext toolContext)
    {
        McpContext context = getContext(toolContext);
        String permError = checkReadPermission(context);
        if (null != permError)
            return permError;
        var json = _listTablesForSchema(context, quotedSchemaName);
        return json.toString();
    }

    @Tool(description = "Provide list of database schemas")
    String listSchemas(ToolContext toolContext)
    {
        McpContext context = getContext(toolContext);
        String permError = checkReadPermission(context);
        if (null != permError)
            return permError;
        var map = _listAllSchemas(DefaultSchema.get(context.getUser(), context.getContainer()));
        var array = new JSONArray();
        for (var entry : map.entrySet())
        {
                UserSchema schema = entry.getValue();
                if (!schema.canReadSchema())
                    continue;
                array.put(new JSONObject(Map.of(
                        "name", entry.getKey().getName(),
                        "quotedName", entry.getKey().toSQLString(),
                        "description", StringUtils.trimToEmpty(schema.getDescription())
                )));
        }
        return new JSONObject(Map.of("success", "true", "schemas", array)).toString();
    }


    @Tool(description = """
            List database schemas across all containers (projects and folders) on the server that the current user \
            has access to. By default, this lists schemas for all containers on the server. \
            Provide a containerPath to query a specific subtree. Each container can have its own \
            set of schemas and tables. Use listTablesForSchema with the schema names returned here \
            to drill deeper. Note: the current folder's schemas are also available via the listSchemas tool.""")
    String listSchemasAcrossServer(
            @ToolParam(required = false, description = "Optional container path to query schemas for (e.g. '/MyProject' or '/MyProject/SubFolder'). If not provided, schemas are listed for all containers on the server.") String containerPath,
            ToolContext toolContext
    )
    {
        McpContext context = getContext(toolContext);

        List<Container> containers;
        if (isNotBlank(containerPath))
        {
            Container c = ContainerManager.getForPath(containerPath);
            if (null == c)
                return new JSONObject(Map.of("success", Boolean.FALSE, "error", "Container not found: " + containerPath)).toString();
            if (!c.hasPermission(context.getUser(), ReadPermission.class))
                return new JSONObject(Map.of("success", Boolean.FALSE, "error", "You don't have read access to the folder: " + containerPath)).toString();
            containers = ContainerManager.getAllChildren(c, context.getUser(), ReadPermission.class);
        }
        else
        {
            Container root = ContainerManager.getRoot();
            containers = ContainerManager.getAllChildren(root, context.getUser(), ReadPermission.class);
        }

        //LOG.info("listSchemasAcrossServer: user={}, isAdmin={}, containers found with ReadPermission={}",
        //        context.getUser().getEmail(), context.getUser().hasSiteAdminPermission(), containers.size());
        LOG.info("listSchemasAcrossServer: container paths={}", containers.stream().map(Container::getPath).toList());

        JSONArray containerArray = new JSONArray();
        for (Container c : containers)
        {
            DefaultSchema defaultSchema = DefaultSchema.get(context.getUser(), c);
            var schemaMap = _listAllSchemas(defaultSchema);
            if (schemaMap.isEmpty())
                continue;

            JSONArray schemas = new JSONArray();
            for (var entry : schemaMap.entrySet())
            {
                UserSchema schema = entry.getValue();
                if (!schema.canReadSchema())
                    continue;
                schemas.put(new JSONObject(Map.of(
                        "name", entry.getKey().getName(),
                        "quotedName", entry.getKey().toSQLString(),
                        "description", StringUtils.trimToEmpty(schema.getDescription())
                )));
            }
            if (schemas.isEmpty())
                continue;

            JSONObject containerObj = new JSONObject();
            containerObj.put("containerPath", c.getPath());
            containerObj.put("containerName", c.getName());
            containerObj.put("schemas", schemas);
            containerArray.put(containerObj);
        }

        return new JSONObject(Map.of(
                "success", Boolean.TRUE,
                "containers", containerArray,
                "totalContainers", containerArray.length()
        )).toString();
    }

    private static final int MAX_ROWS = 100;

    @Tool(description = """
            Execute a LabKey SQL query against a schema and return the result rows as JSON. \
            Use the listSchemas and listTablesForSchema tools first to discover available schemas and tables. \
            Use the listColumnMetaData tool to understand column types before writing your query. \
            The SQL dialect is LabKey SQL (similar to standard SQL with some extensions). \
            Results are capped at 100 rows. Use LIMIT in your SQL if you need fewer rows. \
            For aggregate questions (counts, sums, averages), use GROUP BY and aggregate functions in the SQL.""")
    String executeQuery(
            @ToolParam(description = "Fully qualified schema name as it would appear in SQL e.g. \"study\" or \"lists\"") String quotedSchemaName,
            @ToolParam(description = "LabKey SQL query to execute e.g. SELECT ParticipantId, COUNT(*) as cnt FROM Demographics GROUP BY ParticipantId") String sql,
            ToolContext toolContext
    )
    {
        McpContext context = getContext(toolContext);
        String permError = checkReadPermission(context);
        if (null != permError)
            return permError;
        if (isNotBlank(quotedSchemaName))
            quotedSchemaName = quotedSchemaName.strip();
        if (StringUtils.isBlank(sql))
            return new JSONObject(Map.of("success", Boolean.FALSE, "error", "SQL query is required")).toString();

        SchemaKey schemaKey;
        if (quotedSchemaName.startsWith("\"") && quotedSchemaName.endsWith("\""))
        {
            String[] parts = StringUtils.strip(quotedSchemaName, "\"").split("\"\\.\"");
            schemaKey = SchemaKey.fromParts(parts);
        }
        else
        {
            String[] parts = StringUtils.split(quotedSchemaName, ".");
            schemaKey = SchemaKey.fromParts(parts);
        }

        var defaultSchema = DefaultSchema.get(context.getUser(), context.getContainer());
        var schema = DefaultSchema.resolve(defaultSchema, schemaKey);
        if (null == schema)
            return new JSONObject(Map.of("success", Boolean.FALSE, "error", "Could not find schema: " + quotedSchemaName)).toString();

        try (ResultSet rs = QueryService.get().select(schema, sql))
        {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            JSONArray columns = new JSONArray();
            for (int i = 1; i <= columnCount; i++)
                columns.put(metaData.getColumnName(i));

            JSONArray rows = new JSONArray();
            int rowCount = 0;
            while (rs.next() && rowCount < MAX_ROWS)
            {
                JSONObject row = new JSONObject();
                for (int i = 1; i <= columnCount; i++)
                {
                    String colName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(colName, null != value ? value : JSONObject.NULL);
                }
                rows.put(row);
                rowCount++;
            }

            boolean truncated = !rs.isAfterLast() && rowCount >= MAX_ROWS;

            var ret = new JSONObject();
            ret.put("success", Boolean.TRUE);
            ret.put("columns", columns);
            ret.put("rows", rows);
            ret.put("rowCount", rowCount);
            if (truncated)
                ret.put("truncated", Boolean.TRUE);
            return ret.toString();
        }
        catch (Exception e)
        {
            return new JSONObject(Map.of(
                    "success", Boolean.FALSE,
                    "error", "Query execution failed: " + e.getMessage()
            )).toString();
        }
    }

    @Tool(description = "Provide the SQL source for a saved query.")
    String getSourceForSavedQuery(@ToolParam(description = "Fully qualified query name as it would appear in SQL e.g. \"schema\".\"table or query\"") String fullQuotedTableName, ToolContext toolContext)
    {
        McpContext context = getContext(toolContext);
        String permError = checkReadPermission(context);
        if (null != permError)
            return permError;
        var json = _listTablesForSchema(context, fullQuotedTableName);
        if (json.has("sql"))
            return "```sql\n" + json.getString("sql") + "\n```\n";
        else
            return "I could not find the source for " + fullQuotedTableName;
    }


    @Tool(description = """
            Save addition information for database columns.  If additional metadata is gathered via
            chat, it can be saved to improve further interactions.
            """)
    String saveColumnDescription(
            @ToolParam(description = "Fully qualified table or query name as it would appear in SQL e.g. \"schema\".\"table or query\"")
                String fullQuotedTableName,
            @ToolParam(description = "Quoted column name as it would appear in SQL e.g. \"column name\"")
                String quotedColumnName,
            @ToolParam(description = "Additional metadata to remember for future use.  This will replace any currently saved value")
                String columnMetadata,
            ToolContext toolContext
    )
    {
        McpContext context = getContext(toolContext);
        var map = PropertyManager.getWritableProperties(context.getContainer(), "QueryMCP.annotations", true);
        String fullPath = normalizeIdentifier(fullQuotedTableName + "." + quotedColumnName);
        map.put(fullPath, columnMetadata);
        try (var ignore = SpringActionController.ignoreSqlUpdates())
        {
            map.save();
        }
        return new JSONObject(Map.of("success",Boolean.TRUE)).toString();
    }

    static McpContext getContext(ToolContext toolContext)
    {
        McpContext ctx = McpContext.fromToolContext(toolContext);
        if (null != ctx)
        {
            //LOG.info("MCP tool context resolved from ToolContext: user={}, container={}", ctx.getUser().getEmail(), ctx.getContainer().getPath());
            return ctx;
        }
        ctx = McpContext.get();
        //LOG.info("MCP tool context resolved from ThreadLocal: user={}, container={}", ctx.getUser().getEmail(), ctx.getContainer().getPath());
        return ctx;
    }

    /* For now, list all schemas.  CONSIDER support incremental querying. */
    public static Map<SchemaKey, UserSchema> _listAllSchemas(DefaultSchema root)
    {
        SimpleSchemaTreeVisitor<Map<SchemaKey,UserSchema>, Void> visitor = new SimpleSchemaTreeVisitor<>(false)
        {
            @Override
            public Map<SchemaKey,UserSchema> visitUserSchema(UserSchema schema, Path path, Void v)
            {
                Map<SchemaKey,UserSchema> r = Map.of(schema.getSchemaPath(),schema);
                return visitAndReduce(schema.getUserSchemas(false), path, null, r);
            }

            @Override
            public Map<SchemaKey, UserSchema> reduce(Map<SchemaKey, UserSchema> r1, Map<SchemaKey, UserSchema> r2)
            {
                if (null == r1 || null == r2)
                    return null==r1 && null==r2 ? Map.of() : null==r1 ? r2 : r1;
                var ret = new TreeMap<SchemaKey, UserSchema>();
                ret.putAll(r1);
                ret.putAll(r2);
                return ret;
            }
        };

        // DefaultSchema does not implement UserSchema which is inconvenient.
        TreeMap<SchemaKey,UserSchema> ret = new TreeMap<>();
        for (String name : root.getUserSchemaNames(false))
        {
            UserSchema s = root.getUserSchema(name);
            if (null != s)
            {
                var res = visitor.visit(s, null, null);
                ret.putAll(res);
            }
        }
        return ret;
    }


    public static JSONObject _listTablesForSchema(McpContext context, String fullQuotedName)
    {
        SchemaKey fullKey;

        // TODO : correct method for parsing quoted identifier
        if (fullQuotedName.startsWith("\"") && fullQuotedName.endsWith("\""))
        {
            String[] parts = StringUtils.strip(fullQuotedName, "\"").split("\"\\.\"");
            fullKey = SchemaKey.fromParts(parts);
        }
        else
        {
            String[] parts = StringUtils.split(fullQuotedName, ".");
            fullKey = SchemaKey.fromParts(parts);
        }

        var defaultSchema = DefaultSchema.get(context.getUser(), context.getContainer());
        var schema = DefaultSchema.resolve(defaultSchema, fullKey);
        if (!(schema instanceof UserSchema userSchema))
            return new JSONObject("error", "could not find schema for : " + fullQuotedName);

        JSONArray array = new JSONArray();
        CaseInsensitiveHashSet names = new CaseInsensitiveHashSet(schema.getTableNames());
        var qds = userSchema.getQueryDefs();
        names.addAll(qds.keySet());

        for (String tableName : names)
        {
            // CONSIDER schema.getTableDescription()???
            TableInfo td;
            try
            {
                td = schema.getTable(tableName, null);
                if (null == td)
                    continue;
            }
            catch (QueryParseException qpe)
            {
                continue;
            }
            QueryDefinition qd = ((UserSchema)schema).getQueryDef(tableName);
            JSONObject table = new JSONObject();
            table.put("schemaName", schema.getName());
            table.put("tableName", td.getName());
            table.put("fullQuotedName", new SchemaKey(schema.getSchemaPath(), td.getName()).toSQLString());
            table.put("description", td.getDescription());
            table.put("type", null==qd ? "TABLE" : "QUERY");
            array.put(table);
        }

        var ret = new JSONObject();
        ret.put("schemaName", schema.getName());
        ret.put("fullQuotedName", schema.getSchemaPath().toSQLString());
        if (isNotBlank(schema.getDescription()))
            ret.put("description", schema.getDescription());
        ret.put("tables", array);
        return ret;
    }

    public static JSONObject _listColumnsForTable(McpContext context, String fullQuotedName)
    {
        QueryKey fullKey = dottedIdentifier(fullQuotedName);
        SchemaKey schemaKey;

        var props = PropertyManager.getProperties(context.getContainer(), "QueryMCP.annotations");

        String tableName;
        if (fullKey.size() > 1)
        {
            schemaKey = SchemaKey.fromParts(fullKey.getParent().getParts());
            tableName = fullKey.getName();
        }
        else if (fullKey.size() == 1)
        {
            schemaKey = SchemaKey.fromParts("study");
            tableName = fullKey.getName();
        }
        else
        {
            return new JSONObject("error", "could not find table");
        }

        SchemaKey tableKey = new SchemaKey(schemaKey, tableName);

        var defaultSchema = DefaultSchema.get(context.getUser(), context.getContainer());

        var schema = DefaultSchema.resolve(defaultSchema, schemaKey);
        if (null == schema)
            return new JSONObject("error", "could not find table");

        TableInfo td = schema.getTable(tableName, null);
        if (null == td)
            return new JSONObject("error", "could not find table");

        String sourceSQL = null;
        if (schema instanceof UserSchema userSchema)
        {
            QueryDefinition d = userSchema.getQueryDef(tableName);
            if (null != d)
                sourceSQL = d.getSql();
        }

        JSONObject table = new JSONObject();
        table.put("schemaName", schema.getName());
        table.put("tableName", td.getName());
        table.put("fullQuotedName", new SchemaKey(schema.getSchemaPath(), td.getName()).toSQLString());
        if (isNotBlank(td.getDescription()))
            table.put("description", td.getDescription());
        if (isNotBlank(sourceSQL))
            table.put("sql", sourceSQL);

        var pkColumns = td.getPkColumns();
        var pk = pkColumns.size() == 1 ? pkColumns.get(0).getFieldKey() : null;
        JSONArray columns = new JSONArray();
        for (ColumnInfo col : td.getColumns())
        {
            String columnPropsKey = new SchemaKey(tableKey, col.getName()).toSQLString(true).toLowerCase();
            String extra = props.get(columnPropsKey);

            JSONObject md = new JSONObject();
            md.put("name", col.getName());
            md.put("label", col.getLabel());
            md.put("type", col.getJdbcType().name());
            String description = "";
            description += Objects.toString(col.getDescription(), col.getLabel());
            description += "\n" + Objects.toString(extra, "");
            if (null != col.getDescription())
                md.put("description", description.strip());
            if (col.getFieldKey().equals(pk))
                md.put("is_primary_key", Boolean.TRUE);
            var fk = col.getFk();
            if (null != fk)
            {
                if (fk instanceof QueryForeignKey qfk)
                {
                    SchemaKey qfkSchema = qfk.getLookupSchemaKey();
                    SchemaKey qfkTable = new SchemaKey(qfkSchema, qfk.getLookupTableName());
                    SchemaKey qfkColumn = new SchemaKey(qfkTable, qfk.getLookupColumnName());
                    md.put("is_foreign_key", Boolean.TRUE);
                    md.put("references", qfkColumn.toSQLString());
                }
                else
                {
                    TableDescription references = fk.getLookupTableDescription();
                    if (null != references && references.isPublic())
                    {
                        SchemaKey qfkTable = SchemaKey.fromParts(td.getSchema().getQuerySchemaName(), td.getName());
                        SchemaKey qfkColumn = new SchemaKey(qfkTable, fk.getLookupColumnName());
                        md.put("is_foreign_key", Boolean.TRUE);
                        md.put("references", qfkColumn.toSQLString());
                    }
                }
            }
            columns.put(md);
        }
        table.put("columns",columns);

        return table;
    }


    static QueryKey<?> dottedIdentifier(String compoundIdentifier)
    {
        return new SqlParser().parseIdentifier(compoundIdentifier);
    }


    // QueryKey supports toSQLString(), but not parseSQLString()?  Does parsing code for this exist outside of SqlBase.g?
    static String normalizeIdentifier(String compoundIdentifier)
    {
        return new SqlParser().parseIdentifier(compoundIdentifier).toSQLString(true).toLowerCase();
    }


    /** JSON schema example provided by GEMINI, using triple tick-marks to delimit the machine-readable structured data
     *
     * Here is the database schema in JSON format:
     * ```{
     *   "database": "ecommerce",
     *   "tables": [
     *     {
     *       "name": "customers",
     *       "description": "Stores customer details, including their contact information and location.",
     *       "columns": [
     *         {"name": "customer_id", "type": "INTEGER", "description": "Unique identifier for each customer.", "is_primary_key": true},
     *         {"name": "first_name", "type": "VARCHAR(50)", "description": "The customer's first name."},
     *         {"name": "email", "type": "VARCHAR(100)", "description": "The customer's email address.", "is_unique": true}
     *       ]
     *     },
     *     {
     *       "name": "orders",
     *       "description": "Tracks customer orders.",
     *       "columns": [
     *         {"name": "order_id", "type": "INTEGER", "description": "Unique identifier for each order.", "is_primary_key": true},
     *         {"name": "customer_id", "type": "INTEGER", "description": "Foreign key linking to the customers table.", "is_foreign_key": true, "references": "customers.customer_id"},
     *         {"name": "order_date", "type": "DATE", "description": "The date the order was placed."}
     *       ]
     *     }
     *   ]
     * }```
     */
}

