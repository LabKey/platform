package org.labkey.query.controllers;

import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableDescription;
import org.labkey.api.data.TableInfo;
import org.labkey.api.mcp.McpService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleSchemaTreeVisitor;
import org.labkey.api.query.UserSchema;
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
            uri = "resource://org/labkey/query/controllers/LabKeySql.md",
            mimeType = "application/markdown",
            name = "LabKey SQL",
            description = "Provide documentation for LabKey SQL specific syntax")
    public McpSchema.ReadResourceResult getLabKeySQLDocumentation() throws IOException
    {
        incrementResourceReadCount("LabKey SQL");
        String markdown = IOUtils.resourceToString("org/labkey/query/controllers/LabKeySql.md", null, QueryController.class.getClassLoader());
        return new McpSchema.ReadResourceResult(List.of(
            new McpSchema.TextResourceContents(
                "resource://org/labkey/query/controllers/LabKeySql.md",
                "application/markdown",
                markdown
            )
        ));
    }

    @Tool(description = "Provide column metadata for a sql table. This tool will also return SQL source for saved queries.")
    String listColumns(ToolContext toolContext, @ToolParam(description = "Fully qualified table name as it would appear in SQL e.g. \"schema\".\"table\"") String fullQuotedTableName)
    {
        var json = _listColumns(fullQuotedTableName, toolContext);
        // can I just return a JSONObject
        return json.toString();
    }

    @Tool(description = "Provide list of tables within the provided schema.")
    String listTables(ToolContext toolContext, @ToolParam(description = "Fully qualified schema name as it would appear in SQL e.g. \"schema\"") String quotedSchemaName)
    {
        var json = _listTables(quotedSchemaName, getContext(toolContext));
        // can I just return a JSONObject
        return json.toString();
    }

    @Tool(description = "Provide list of database schemas")
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
        return new JSONObject(Map.of("success", "true", "schemas", array)).toString();
    }


    @Tool(description = "Provide the SQL source for a saved query.")
    String getSourceForSavedQuery(ToolContext toolContext, @ToolParam(description = "Fully qualified query name as it would appear in SQL e.g. \"schema\".\"table or query\"") String fullQuotedTableName)
    {
        var json = _listTables(fullQuotedTableName, getContext(toolContext));
        if (json.has("sql"))
            return "```sql\n" + json.getString("sql") + "\n```\n";
        else
            return "I could not find the source for " + fullQuotedTableName;
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


    public static JSONObject _listTables(String fullQuotedName, ContainerUser cu)
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

        var defaultSchema = DefaultSchema.get(cu.getUser(), cu.getContainer());
        var schema = DefaultSchema.resolve(defaultSchema, fullKey);
        if (!(schema instanceof UserSchema userSchema))
            throw new NotFoundException("Could not find schema for " + fullQuotedName);

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

    public JSONObject _listColumns(String fullQuotedName, ToolContext toolContext)
    {
        var context = getContext(toolContext);
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
            throw new NotFoundException("You need to provide a fully qualified schema and table");
        }
        else
        {
            throw new NotFoundException("Could not find table " + fullQuotedName);
        }

        SchemaKey tableKey = new SchemaKey(schemaKey, tableName);

        var defaultSchema = DefaultSchema.get(context.getUser(), context.getContainer());

        var schema = DefaultSchema.resolve(defaultSchema, schemaKey);
        if (null == schema)
            throw new NotFoundException("Could not find schema for : " + fullQuotedName);

        TableInfo td = schema.getTable(tableName, null);
        if (null == td)
            throw new NotFoundException("Could not find table for : " + fullQuotedName);

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

