package org.labkey.query.controllers;

import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableDescription;
import org.labkey.api.data.TableInfo;
import org.labkey.api.mcp.McpException;
import org.labkey.api.mcp.McpInternal;
import org.labkey.api.mcp.McpService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleSchemaTreeVisitor;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.query.sql.SqlParser;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class QueryMcp implements McpService.McpImpl
{
    @McpResource(
        uri = "resource://org/labkey/query/controllers/prompts/LabKeySql.md",
        mimeType = "application/markdown",
        name = "LabKey SQL",
        description = "Provide documentation for LabKey SQL specific syntax")
    public ReadResourceResult getLabKeySQLDocumentation() throws IOException
    {
        incrementResourceRequestCount("LabKey SQL");
        String markdown = IOUtils.resourceToString("org/labkey/query/controllers/prompts/LabKeySql.md", null, QueryController.class.getClassLoader());
        return new ReadResourceResult(List.of(
            new TextResourceContents(
                "resource://org/labkey/query/controllers/prompts/LabKeySql.md",
                "application/markdown",
                markdown
            )
        ));
    }

    @Tool(description = "Provide list of database schemas")
    @RequiresPermission(ReadPermission.class)
    String listSchemas(ToolContext toolContext)
    {
        ContainerUser cu = getContext(toolContext);
        var map = _listAllSchemas(DefaultSchema.get(cu.getUser(), cu.getContainer()));
        var array = new JSONArray();
        for (var entry : map.entrySet())
        {
            array.put(new JSONObject(Map.of(
                "name", entry.getKey().getName(),
                "quotedName", entry.getKey().toSQLString(),
                "description", StringUtils.trimToEmpty(entry.getValue().getDescription())
            )));
        }
        return new JSONArray(array).toString();
    }

    @Tool(description = "Provide list of tables within the provided schema.")
    @RequiresPermission(ReadPermission.class)
    String listTables(ToolContext toolContext, @ToolParam(description = "Fully qualified schema name as it would appear in SQL e.g. Study or \"Study\".\"Datasets\"") String schemaName)
    {
        var json = _listTables(getContext(toolContext), schemaName);
        return json.toString();
    }

    @Tool(description = "Provide column metadata for a sql table. The metadata includes SQL source for saved queries.")
    @RequiresPermission(ReadPermission.class)
    String listColumns(
        ToolContext toolContext,
        @ToolParam(description = "Fully qualified schema name as it would appear in SQL e.g. Study or \"Study.Datasets\"") String schemaName,
        @ToolParam(description = "Table or query name as it would appear in SQL e.g. MyTable, MyQuery, or \"MyTable\"") String queryName
    )
    {
        var json = _listColumns(toolContext, schemaName, queryName);
        return json.toString();
    }

    @Tool(description = "Provide the SQL source for a saved query.")
    @RequiresPermission(ReadPermission.class)
    String getSourceForSavedQuery(
        ToolContext toolContext,
        @ToolParam(description = "Fully qualified schema name as it would appear in SQL e.g. Study or \"Study\".\"Datasets\"") String schemaName,
        @ToolParam(description = "Table or query name as it would appear in SQL e.g. MyTable, MyQuery, or \"MyTable\"") String queryName
    )
    {
        var json = _listColumns(toolContext, schemaName, queryName);
        if (json.has("sql"))
            return "```sql\n" + json.getString("sql") + "\n```\n";
        else
            throw new NotFoundException("Could not find the source for " + schemaName + "." + queryName);
    }

    @Tool(description = "Validate SQL syntax.")
    @RequiresPermission(ReadPermission.class)
    String validateSQL(
            ToolContext toolContext,
            @ToolParam(description = "Fully qualified schema name as it would appear in SQL e.g. Study or \"Study\".\"Datasets\"") String schemaName,
            @ToolParam(description = "SQL source") String sql
    )
    {
        var context = getContext(toolContext);

        SchemaKey schemaKey = getSchemaKey(schemaName);
        QuerySchema schema = DefaultSchema.get(context.getUser(), context.getContainer(), schemaKey);

        try
        {
            TableInfo ti = QueryService.get().createTable(schema, sql, null, true);
            var warnings = ti.getWarnings();
            if (null != warnings)
            {
                var warning = warnings.stream().findFirst();
                if (warning.isPresent())
                    throw warning.get();
            }
// CONSIDER: add back code to add database validate, but this seems to have stopped working
//            if (ti.getSqlDialect().isPostgreSQL())
//            {
//                var parameters = ti.getNamedParameters();
//                if (parameters.isEmpty())
//                {
//                    SQLFragment sqlPrepare = new SQLFragment("PREPARE validate AS SELECT * FROM ").append(ti.getFromSQL("MYVALIDATEQUERY__"));
//                    new SqlExecutor(ti.getSchema().getScope()).execute(sqlPrepare);
//                }
//            }
        }
        catch (Exception x)
        {
            // CONSIDER remove line line/character information from DB errors as they won't match the LabKey SQL
            return "That SQL caused the " + (x instanceof QueryParseWarning ? "warning" : "error") + " below:\n```" + x.getMessage() + "```";
        }
        return "success";
    }

    @Tool(description = "Validate a SQL expression for a calculated column. The set of available columns and their types, including any PHI-restricted columns, is supplied by the hosting endpoint, not by the caller; you only need to provide the expression itself.")
    @McpInternal("Added for validation for the QueryController.ExpressionAssistantAgentAction endpoint.")
    @RequiresPermission(ReadPermission.class)
    String validateCalculatedColumnExpression(
            ToolContext toolContext,
            @ToolParam(description = "SQL expression for the calculated column") String expression
    )
    {
        var context = getContext(toolContext);

        @SuppressWarnings("unchecked")
        Map<FieldKey, JdbcType> columnMap = (Map<FieldKey, JdbcType>) toolContext.getContext().get("columnMap");
        @SuppressWarnings("unchecked")
        List<FieldKey> phiColumns = (List<FieldKey>) toolContext.getContext().get("phiColumns");

        if (columnMap == null)
            throw new IllegalArgumentException("validateCalculatedColumnExpression requires a columnMap supplied by the endpoint; it cannot be invoked directly.");

        try
        {
            QueryController.parseCalculatedColumn(context.getContainer(), context.getUser(), expression, columnMap, phiColumns);
        }
        catch (QueryException x)
        {
            return "That SQL caused the " + (x instanceof QueryParseWarning ? "warning" : "error") + " below:\n```" + x.getMessage() + "```";
        }

        return "success";
    }

    /* For now, list all schemas. CONSIDER support incremental querying. */
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

    public static JSONObject _listTables(ContainerUser cu, String schemaName)
    {
        var defaultSchema = DefaultSchema.get(cu.getUser(), cu.getContainer());
        var schema = DefaultSchema.resolve(defaultSchema, getSchemaKey(schemaName));

        if (!(schema instanceof UserSchema userSchema))
            throw new NotFoundException("Could not find schema " + schemaName);

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
            QueryDefinition qd = userSchema.getQueryDef(tableName);
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

    public JSONObject _listColumns(ToolContext toolContext, String schemaName, String tableName)
    {
        var context = getContext(toolContext);

        SchemaKey schemaKey = getSchemaKey(schemaName);
        var defaultSchema = DefaultSchema.get(context.getUser(), context.getContainer());
        var schema = DefaultSchema.resolve(defaultSchema, schemaKey);
        if (null == schema)
            throw new NotFoundException("Could not find schema " + schemaName);

        var props = PropertyManager.getProperties(context.getContainer(), "QueryMCP.annotations");

        QueryKey<?> queryKey = dottedIdentifier(tableName);
        SchemaKey tableKey = new SchemaKey(schemaKey, queryKey.getName());

        TableInfo td = schema.getTable(queryKey.getName(), null);
        if (null == td)
            throw new NotFoundException("Could not find table " + schemaName + "." + tableName);

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
            table.put("sql", sourceSQL.trim());

        var pkColumns = td.getPkColumns();
        var pk = pkColumns.size() == 1 ? pkColumns.getFirst().getFieldKey() : null;
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
        table.put("columns", columns);

        return table;
    }

    private static SchemaKey getSchemaKey(String schemaName)
    {
        final String[] parts;

        // TODO : correct method for parsing quoted identifier
        if (schemaName.startsWith("\"") && schemaName.endsWith("\""))
        {
            parts = StringUtils.strip(schemaName, "\"").split("\"\\.\"");
        }
        else
        {
            parts = StringUtils.split(schemaName, ".");
        }

        return SchemaKey.fromParts(parts);
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

    /* JSON schema example provided by GEMINI, using triple tick-marks to delimit the machine-readable structured data
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
