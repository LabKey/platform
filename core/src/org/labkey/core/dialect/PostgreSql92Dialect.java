/*
 * Copyright (c) 2012-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.dialect;

import jakarta.servlet.ServletException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Constraint;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseIdentifier;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.InClauseGenerator;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterMarkerInClauseGenerator;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableInClauseGenerator;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.dialect.BasePostgreSqlDialect;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StandardJdbcHelper;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.template.Warnings;
import org.labkey.core.admin.sql.ScriptReorderer;
import org.springframework.jdbc.BadSqlGrammarException;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 * This is the base class defining PostgreSQL-specific (i.e., not Redshift) behavior. PostgreSQL 9.2 is no longer
 * supported; however, we keep this class to track changes we implemented specifically for this version.
 */
abstract class PostgreSql92Dialect extends BasePostgreSqlDialect
{
    public static final String PRODUCT_NAME = "PostgreSQL";

    // This has been the standard PostgreSQL identifier max byte length for many years. However, this could change in
    // the future plus servers can be compiled with a different limit, so we query this setting on first connection to
    // each database.
    private int _maxIdentifierByteLength = 63;
    private InClauseGenerator _inClauseGenerator;

    private final TempTableInClauseGenerator _tempTableInClauseGenerator = new TempTableInClauseGenerator();
    private final AtomicBoolean _arraySortFunctionExists = new AtomicBoolean(false);

    @Override
    public void handleCreateDatabaseException(SQLException e) throws ServletException
    {
        if ("55006".equals(e.getSQLState()))
        {
            LOG.error("You must close down pgAdmin III and all other applications accessing PostgreSQL.");
            throw (new ServletException("Close down or disconnect pgAdmin III and all other applications accessing PostgreSQL", e));
        }
        else
        {
            super.handleCreateDatabaseException(e);
        }
    }

    @Override
    public void prepareDriver(Class<Driver> driverClass)
    {
        // PostgreSQL driver 42.0.0 added logging via the Java Logging API (java.util.logging). This caused the driver to
        // start logging SQLExceptions (such as the initial connection failure on bootstrap) to the console... harmless
        // but annoying. This code suppresses the driver logging.
        Logger pgjdbcLogger = LogManager.getLogManager().getLogger("org.postgresql");

        if (null != pgjdbcLogger)
            pgjdbcLogger.setLevel(Level.OFF);
    }

    // Make sure that the PL/pgSQL language is enabled in the associated database. If not, throw. Since 9.0, PostgreSQL has
    // shipped with PL/pgSQL enabled by default, so the check is no longer critical, but continue to verify just to be safe.
    @Override
    public void prepareNewLabKeyDatabase(DbScope scope)
    {
        if (new SqlSelector(scope, "SELECT * FROM pg_language WHERE lanname = 'plpgsql'").exists())
            return;

        String dbName = scope.getDatabaseName();
        String message = "PL/pgSQL is not enabled in the \"" + dbName + "\" database because it is not enabled in your Template1 master database.";
        String advice = "Use PostgreSQL's 'createlang' command line utility to enable PL/pgSQL in the \"" + dbName + "\" database then restart Tomcat.";

        throw new ConfigurationException(message, advice);
    }

    @Override
    public String prepare(DbScope scope)
    {
        initializeInClauseGenerator(scope);
        determineIfArraySortFunctionExists(scope);
        return super.prepare(scope);
    }

    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.add("collation");

        return words;
    }

    /*
    These override method implementations were migrated from PostgreSql91Dialect when that class was promoted to api:
        getProductName()
        createStringHandler()
        getJdbcHelper()
        getScriptWarnings()
        initializeInClauseGenerator()
     */

    @Override
    public String getProductName()
    {
        return PRODUCT_NAME;
    }

    // Query PostgreSQL-specific settings
    @Override
    protected void determineSettings(DbScope scope)
    {
        if (getServerType().supportsSpecialMetadataQueries())
        {
            super.determineSettings(scope);

            String value = new SqlSelector(scope, "SELECT setting FROM pg_settings WHERE name = 'max_identifier_length'").getObject(String.class);
            try
            {
                _maxIdentifierByteLength = Integer.valueOf(value);
            }
            catch (NumberFormatException e)
            {
                LOG.error("Couldn't parse max_identifier_length; continuing with default value of {}", _maxIdentifierByteLength, e);
            }
        }
    }

    @Override
    protected DialectStringHandler createStringHandler()
    {
        // TODO: Isn't this the wrong setting?  Should we be looking at the "backslash_quote" setting instead?
        if (getStandardConformingStrings())
            return super.createStringHandler();
        else
            return new PostgreSqlNonConformingStringHandler();
    }

    /*
        PostgreSQL example connection URLs we need to parse:

        jdbc:postgresql:database
        jdbc:postgresql://host/database
        jdbc:postgresql://host:port/database
        jdbc:postgresql:database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host/database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host:port/database?user=fred&password=secret&ssl=true
    */
    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new StandardJdbcHelper(PostgreSqlDialectFactory.JDBC_PREFIX);
    }

    @Override
    public String getDefaultDatabaseName()
    {
        return "template1";
    }

    @Override
    public boolean canExecuteUpgradeScripts()
    {
        return true;
    }

    @Override
    public Collection<String> getScriptWarnings(String name, String sql)
    {
        // Strip out all block- and single-line comments
        Pattern commentPattern = Pattern.compile(ScriptReorderer.COMMENT_REGEX, Pattern.DOTALL + Pattern.MULTILINE);
        Matcher matcher = commentPattern.matcher(sql);
        String noComments = matcher.replaceAll("");

        List<String> warnings = new LinkedList<>();

        // Split statements by semicolon and CRLF
        for (String statement : noComments.split(";[\\n\\r]+"))
        {
            if (Strings.CI.startsWith(statement.trim(), "SET "))
                warnings.add(statement);
        }

        return warnings;
    }

    @Override
    public String getSQLScriptPath()
    {
        return "postgresql";
    }

    @Override
    public String getUniqueIdentType()
    {
        return "SERIAL";
    }

    @Override
    public boolean supportsGroupConcat()
    {
        return getServerType().supportsGroupConcat();
    }

    @Override
    public boolean supportsSelectConcat()
    {
        return true;
    }

    @Override
    public SQLFragment getSelectConcat(SQLFragment selectSql, String delimiter)
    {
        SQLFragment result = new SQLFragment("array_to_string(array(");
        result.append(selectSql);
        result.append("), ");
        result.append(getStringHandler().quoteStringLiteral(delimiter));
        result.append(")");
        return result;
    }

    // Does this datasource include our sort array function? The LabKey datasource should always have it, but external datasources might not
    private void determineIfArraySortFunctionExists(DbScope scope)
    {
        if (getServerType().supportsSpecialMetadataQueries())
        {
            Selector selector = new SqlSelector(scope, "SELECT * FROM pg_catalog.pg_namespace n INNER JOIN pg_catalog.pg_proc p ON pronamespace = n.oid WHERE nspname = 'core' AND proname = 'sort'");
            _arraySortFunctionExists.set(selector.exists());
        }

        // Array sort function should always exist in LabKey scope (for now)
        assert !scope.isLabKeyScope() || _arraySortFunctionExists.get();
    }

    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted, @NotNull SQLFragment delimiterSQL, boolean includeNulls)
    {
        // Sort function might not exist in external datasource; skip that syntax if not
        boolean useSortFunction = sorted && _arraySortFunctionExists.get();
        SQLFragment result = new SQLFragment();

        if (useSortFunction)
        {
            result.append("array_to_string(");
            result.append("core.sort(");   // TODO: Switch to use ORDER BY option inside array aggregate instead of our custom function
            result.append("array_agg(");
            if (distinct)
            {
                result.append("DISTINCT ");
            }

            if (includeNulls)
            {
                result.append("COALESCE(CAST(");
                result.append(sql);
                result.append(" AS VARCHAR), '')");
            }
            else
            {
                result.append(sql);
            }

            result.append(")"); // array_agg
            result.append(")"); // core.sort
        }
        else
        {
            result.append("string_agg(");
            if (distinct)
            {
                result.append("DISTINCT ");
            }

            if (includeNulls)
            {
                result.append("COALESCE(");
                result.append(sql);
                result.append("::text, '')");
            }
            else
            {
                result.append(sql);
                result.append("::text");
            }
        }

        result.append(", ");
        result.append(delimiterSQL);
        result.append(")"); // array_to_string | string_agg

        return result;
    }

    @Override
    public SQLFragment getAnalyzeCommandForTable(String tableName)
    {
        return new SQLFragment("ANALYZE ").appendIdentifier(tableName);
    }

    private void initializeInClauseGenerator(DbScope scope)
    {
        _inClauseGenerator = getJdbcVersion(scope) >= 4 ? new ArrayParameterInClauseGenerator(scope) : new ParameterMarkerInClauseGenerator();
    }

    @Override
    public InClauseGenerator getDefaultInClauseGenerator()
    {
        return _inClauseGenerator;
    }

    @Override
    public TempTableInClauseGenerator getTempTableInClauseGenerator()
    {
        return _tempTableInClauseGenerator;
    }

    @Override
    public void addAdminWarningMessages(Warnings warnings, boolean showAllWarnings)
    {
        super.addAdminWarningMessages(warnings, showAllWarnings);
        if (showAllWarnings)
            warnings.add(HtmlString.of(PostgreSqlDialectFactory.getStandardWarningMessage("has not been tested against", getMajorVersion() + ".x")));
    }

    private int getIdentifierMaxByteLength()
    {
        return _maxIdentifierByteLength;
    }

    @Override
    public boolean isIdentifierTooLong(String identifier)
    {
        return identifier.getBytes(StandardCharsets.UTF_8).length > getIdentifierMaxByteLength();
    }

    @Override
    public String truncateAndJoin(String... parts)
    {
        String ret = String.join("$", parts);

        if (isIdentifierTooLong(ret))
        {
            int maxBytes = getIdentifierMaxByteLength();
            StringBuilder sb = new StringBuilder(maxBytes);
            int partsLength = parts.length;
            int remainingBytes = maxBytes - partsLength + 1; // Make room for dollar signs
            for (int i = 0; i < partsLength; i++)
            {
                String truncated = truncateBytes(parts[i], remainingBytes / (partsLength - i));
                if (i > 0)
                    sb.append("$");
                sb.append(truncated);
                remainingBytes -= truncated.getBytes(StandardCharsets.UTF_8).length;
            }
            ret = sb.toString();
            assert ret.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
        }

        return ret;
    }

    @Override
    public String truncate(String str, int reserved)
    {
        return truncateBytes(str, getIdentifierMaxByteLength() - reserved);
    }

    // Truncates based on UTF-8 bytes
    private static String truncateBytes(String str, int maxBytes)
    {
        if (maxBytes < 13)
            throw new IllegalStateException("maxBytes for legal name too small: " + maxBytes);
        int len = str.getBytes(StandardCharsets.UTF_8).length;
        if (len > maxBytes)
        {
            String prefix = generateIdentifierPrefix(str);
            str = prefix + StringUtilsLabKey.rightUtf8Bytes(str, maxBytes - prefix.getBytes(StandardCharsets.UTF_8).length);
        }
        assert str.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
        assert !StringUtilsLabKey.hasBrokenSurrogate(str);
        return str;
    }

    @Override
    public boolean canShowExecutionPlan(ExecutionPlanType type)
    {
        return true;
    }

    @Override
    protected Collection<String> getQueryExecutionPlan(Connection conn, DbScope scope, SQLFragment sql, ExecutionPlanType type)
    {
        SQLFragment copy = new SQLFragment(sql);
        copy.insert(0, type == ExecutionPlanType.Estimated ? "EXPLAIN " : "EXPLAIN ANALYZE ");

        return new SqlSelector(scope, conn, copy).getCollection(String.class);
    }

    @Override
    // No need to split up PostgreSQL scripts; execute all statements in a single block (unless we have a special stored proc call).
    protected Pattern getSQLScriptSplitPattern()
    {
        return null;
    }

    private static final Pattern PROC_PATTERN = Pattern.compile("^\\s*SELECT\\s+core\\.(executeJava(?:Upgrade|Initialization)Code\\s*\\(\\s*'(.+)'\\s*\\))\\s*;\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @NotNull
    @Override
    protected Pattern getSQLScriptProcPattern()
    {
        return PROC_PATTERN;
    }

    @Override
    protected void checkSqlScript(String lowerNoComments, String lowerNoCommentsNoWhiteSpace, Collection<String> errors)
    {
        if (lowerNoCommentsNoWhiteSpace.contains("setsearch_pathto"))
            errors.add("Do not use \"SET search_path TO <schema>\". Instead, schema-qualify references to all objects.");

        if (!lowerNoCommentsNoWhiteSpace.endsWith(";"))
            errors.add("Script must end with a semicolon");
    }

    @Override
    public @NotNull Collection<Sequence> getAutoIncrementSequences(TableInfo table)
    {
        SQLFragment sql = new SQLFragment("""
            SELECT SchemaName, TableName, ColumnName, LastValue FROM (
                SELECT
                    s.relname AS SequenceName, -- Not used
                    tns.nspname AS SchemaName,
                    t.relname AS TableName,
                    a.attname AS ColumnName,
                    seq.last_value AS LastValue,
                    sns.nspname AS SequenceSchema -- Not used. In theory, sequence could live in a different schema, but not our practice
                FROM
                    pg_depend d
                JOIN
                    pg_class s ON d.objid = s.oid -- The sequence
                JOIN
                    pg_namespace sns ON s.relnamespace = sns.oid
                JOIN
                    pg_class t ON d.refobjid = t.oid -- The table
                JOIN
                    pg_namespace tns ON t.relnamespace = tns.oid
                JOIN
                    pg_attribute a ON d.refobjid = a.attrelid AND d.refobjsubid = a.attnum
                JOIN
                    pg_sequences seq ON s.relname = seq.SequenceName AND tns.nspname = seq.SchemaName -- maybe sns.nspname instead? but that is slower...
                WHERE
                    s.relkind = 'S' -- Sequence
                    AND t.relkind IN ('r', 'P') -- Table (regular table or partitioned table)
                    AND d.deptype IN ('a', 'i') -- Automatic dependency for DEFAULT or index-related for PK
            ) AS x
            WHERE SchemaName ILIKE ? AND TableName ILIKE ?
            """,
            table.getSchema().getName(),
            table.getName()
        );
        return new SqlSelector(table.getSchema(), sql).getCollection(Sequence.class);
    }

    @Override
    public String getBinaryDataType()
    {
        return "BYTEA";
    }

    @Override
    public String getGlobalTempTablePrefix()
    {
        return DbSchema.TEMP_SCHEMA_NAME + ".";
    }

    @Override
    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + indexName;
    }

    @Override
    public String getCreateDatabaseSql(String dbName)
    {
        // This will handle both mixed case and special characters on PostgreSQL
        var legal = makeIdentifierFromMetaDataName(dbName);
        return new SQLFragment("CREATE DATABASE ").appendIdentifier(legal).append(" WITH ENCODING 'UTF8'").getRawSQL();
    }

    @Override
    public String getCreateSchemaSql(String schemaName)
    {
        if (!isLegalName(schemaName) || isReserved(schemaName))
            throw new IllegalArgumentException("Not a legal schema name: " + schemaName);

        //Quoted schema names are bad news
        return "CREATE SCHEMA " + schemaName;
    }

    @Override
    public String getTruncateSql(String tableName)
    {
        // To be consistent with MS SQL server, always restart the sequence.  Note that the default for postgres
        // is to continue the sequence but we don't have this option with MS SQL Server
        return "TRUNCATE TABLE " + tableName + " RESTART IDENTITY";
    }

    @Override
    public List<SQLFragment> getChangeStatements(TableChange change)
    {
        List<SQLFragment> result = new ArrayList<>();
        switch (change.getType())
        {
            case CreateTable -> result.addAll(getCreateTableStatements(change));
            case DropTable -> {
                SQLFragment f = new SQLFragment("DROP TABLE ");
                f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                result.add(f);
            }
            case AddColumns -> result.addAll(getAddColumnsStatements(change));
            case DropColumns -> result.add(getDropColumnsStatement(change));
            case RenameColumns -> result.addAll(getRenameColumnsStatement(change));
            case DropIndicesByName -> result.addAll(getDropIndexByNameStatements(change));
            case AddIndices -> result.addAll(getCreateIndexStatements(change));
            case ResizeColumns, ChangeColumnTypes -> result.addAll(getChangeColumnTypeStatement(change));
            case DropConstraints -> result.addAll(getDropConstraintsStatement(change));
            case AddConstraints -> result.addAll(getAddConstraintsStatement(change));
            default -> throw new IllegalArgumentException("Unsupported change type: " + change.getType());
        }

        return result;
    }

    private Collection<? extends SQLFragment> getDropIndexByNameStatements(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        for (String indexName : change.getIndicesToBeDroppedByName())
        {
            statements.add(getDropIndexCommand(change, indexName));
        }
        return statements;
    }

    private SQLFragment getDropIndexCommand(TableChange change, String indexName)
    {
        SQLFragment f = new SQLFragment("DROP INDEX ");
        f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(indexName);
        return f;
    }

    /**
     * We've historically created lower-cased column names in provisioned tables in Postgres. Keep doing that
     * for consistency, though ideally we'd stop doing this and update all existing provisioned tables.
     */
    private DatabaseIdentifier makePropertyIdentifier(String name)
    {
        if (isIdentifierTooLong(name))
            throw new UnsupportedOperationException("Name is too long: " + name);
        return new _DatabaseIdentifier(name, quoteIdentifier(name.toLowerCase()), this);
    }

    /**
     * Generate the Alter Table statement to change the size or type of the column
     * <p>
     * NOTE: expects data size check to be done prior,
     *       will throw a SQL exception if not able to change size due to existing data
     */
    private List<SQLFragment> getChangeColumnTypeStatement(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();

        // Postgres allows executing multiple ALTER COLUMN statements under one ALTER TABLE
        List<SQLFragment> nonDateTimeClauses = new ArrayList<>();

        for (PropertyStorageSpec column : change.getColumns())
        {
            PropertyType oldPropertyType = change.getOldPropTypes().get(column.getName());
            DatabaseIdentifier columnIdent = makePropertyIdentifier(column.getName());
            if (column.getJdbcType().isDateOrTime())
            {
                String tempColumnName = column.getName() + "~~temp~~";
                DatabaseIdentifier tempColumnIdent = makePropertyIdentifier(tempColumnName);

                // 1) ADD temp column
                SQLFragment addTemp = new SQLFragment("ALTER TABLE ");
                addTemp.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                addTemp.append(" ADD COLUMN ").append(getSqlColumnSpec(column, tempColumnName));
                statements.add(addTemp);

                // 2) UPDATE: copy casted value to temp column
                SQLFragment update = new SQLFragment("UPDATE ");
                update.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                update.append(" SET ").appendIdentifier(tempColumnIdent);
                update.append(" = CAST(").appendIdentifier(columnIdent).append(" AS ").append(getSqlTypeName(column)).append(")");
                statements.add(update);

                // 3) DROP original column
                SQLFragment drop = new SQLFragment("ALTER TABLE ");
                drop.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                drop.append(" DROP COLUMN ").appendIdentifier(columnIdent);
                statements.add(drop);

                // 4) RENAME temp column to original column name
                SQLFragment rename = new SQLFragment("ALTER TABLE ");
                rename.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                rename.append(" RENAME COLUMN ").appendIdentifier(tempColumnIdent).append(" TO ").appendIdentifier(columnIdent);
                statements.add(rename);
            }
            else if (oldPropertyType == PropertyType.MULTI_CHOICE && column.getJdbcType().isText())
            {
                // Converting from text[] (array) to text requires an intermediate column and transformation
                String tempColumnName = column.getName() + "~~temp~~";
                DatabaseIdentifier tempColumnIdent = makePropertyIdentifier(tempColumnName);

                // 1) ADD temp column of text type
                SQLFragment addTemp = new SQLFragment("ALTER TABLE ");
                addTemp.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                addTemp.append(" ADD COLUMN ").append(getSqlColumnSpec(column, tempColumnName));
                statements.add(addTemp);

                // 2) UPDATE: convert and copy value to temp column
                //    - NULL array -> NULL
                //    - empty array -> NULL
                //    - non-empty array -> concatenate array elements with comma (', ')
                SQLFragment update = new SQLFragment("UPDATE ");
                update.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                update.append(" SET ").appendIdentifier(tempColumnIdent).append(" = CASE ");
                update.append(" WHEN ").appendIdentifier(columnIdent).append(" IS NULL THEN NULL ");
                update.append(" WHEN COALESCE(array_length(").appendIdentifier(columnIdent).append(", 1), 0) = 0 THEN NULL ");
                update.append(" ELSE array_to_string(").appendIdentifier(columnIdent).append(", ', ') END");
                statements.add(update);

                // 3) DROP original column
                SQLFragment drop = new SQLFragment("ALTER TABLE ");
                drop.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                drop.append(" DROP COLUMN ").appendIdentifier(columnIdent);
                statements.add(drop);

                // 4) RENAME temp column to original column name
                SQLFragment rename = new SQLFragment("ALTER TABLE ");
                rename.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                rename.append(" RENAME COLUMN ").appendIdentifier(tempColumnIdent).append(" TO ").appendIdentifier(columnIdent);
                statements.add(rename);
            }
            else if (column.getJdbcType() == JdbcType.ARRAY)
            {
                // Converting from text to text[] requires an intermediate column and transformation
                String tempColumnName = column.getName() + "~~temp~~";
                DatabaseIdentifier tempColumnIdent = makePropertyIdentifier(tempColumnName);

                // 1) ADD temp column of array type (e.g., text[])
                SQLFragment addTemp = new SQLFragment("ALTER TABLE ");
                addTemp.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                addTemp.append(" ADD COLUMN ").append(getSqlColumnSpec(column, tempColumnName));
                statements.add(addTemp);

                // 2) UPDATE: copy converted value to temp column as single-element array
                //    - NULL or blank ('') -> empty array []
                //    - otherwise -> single-element array [text]
                SQLFragment update = new SQLFragment("UPDATE ");
                update.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                update.append(" SET ").appendIdentifier(tempColumnIdent);
                update.append(" = CASE WHEN ").appendIdentifier(columnIdent).append(" IS NULL OR ").appendIdentifier(columnIdent).append(" = '' THEN ARRAY[]::text[] ELSE ARRAY[");
                update.appendIdentifier(columnIdent).append("]::text[] END");
                statements.add(update);

                // 3) DROP original column
                SQLFragment drop = new SQLFragment("ALTER TABLE ");
                drop.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                drop.append(" DROP COLUMN ").appendIdentifier(columnIdent);
                statements.add(drop);

                // 4) RENAME temp column to original column name
                SQLFragment rename = new SQLFragment("ALTER TABLE ");
                rename.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                rename.append(" RENAME COLUMN ").appendIdentifier(tempColumnIdent).append(" TO ").appendIdentifier(columnIdent);
                statements.add(rename);
            }
            else
            {
                String dbType;
                if (column.getJdbcType().isText())
                {
                    // Using the common default max size to make type change to text
                    dbType = column.getSize() == -1 || column.getSize() > SqlDialect.MAX_VARCHAR_SIZE ?
                        getSqlTypeName(JdbcType.LONGVARCHAR) :
                        getSqlTypeName(column.getJdbcType()) + "(" + column.getSize().toString() + ")";
                }
                else if (column.getJdbcType().isDecimal())
                {
                    dbType = getSqlTypeName(column.getJdbcType()) + DEFAULT_DECIMAL_SCALE_PRECISION;
                }
                else
                {
                    dbType = getSqlTypeName(column.getJdbcType());
                }

                SQLFragment clause = new SQLFragment();
                clause.append("ALTER COLUMN ").appendIdentifier(columnIdent).append(" TYPE ").append(dbType);
                nonDateTimeClauses.add(clause);
            }
        }

        if (!nonDateTimeClauses.isEmpty())
        {
            SQLFragment alter = new SQLFragment("ALTER TABLE ");
            alter.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            alter.append(" ");
            String sep = "";
            for (SQLFragment c : nonDateTimeClauses)
            {
                alter.append(sep).append(c);
                sep = ", ";
            }
            statements.add(alter);
        }

        return statements;
    }

    private List<SQLFragment> getRenameColumnsStatement(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        for (Map.Entry<String, String> oldToNew : change.getColumnRenames().entrySet())
        {
            DatabaseIdentifier oldIdentifier = makePropertyIdentifier(oldToNew.getKey());
            DatabaseIdentifier newIdentifier = makePropertyIdentifier(oldToNew.getValue());
            if (!oldIdentifier.equals(newIdentifier))
            {
                SQLFragment f = new SQLFragment("ALTER TABLE ");
                f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                f.append(" RENAME COLUMN ").appendIdentifier(oldIdentifier).append(" TO ").appendIdentifier(newIdentifier);
                statements.add(f);
            }
        }

        // TODO: This loop should not guess the name of the old indices; instead, it should look them up.
        // TableChange.setIndexedColumns() could set _indexRenames providing the name, and then this code uses that info.
        // Or maybe schemaTableInfo.getAllIndices() and then use Index.isSameIndex() to find names. Issue 53838.
        for (Map.Entry<PropertyStorageSpec.Index, PropertyStorageSpec.Index> oldToNew : change.getIndexRenames().entrySet())
        {
            PropertyStorageSpec.Index oldIndex = oldToNew.getKey();
            PropertyStorageSpec.Index newIndex = oldToNew.getValue();
            String oldName = nameIndex(change.getTableName(), oldIndex.columnNames); // TODO: Look up name
            String newName = nameIndex(change.getTableName(), newIndex.columnNames);
            if (!oldName.equals(newName))
            {
                SQLFragment f = new SQLFragment("ALTER INDEX ");
                f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(oldName);
                f.append(" RENAME TO ").appendIdentifier(newName);
                statements.add(f);
            }
        }

        return statements;
    }

    private SQLFragment getDropColumnsStatement(TableChange change)
    {
        List<SQLFragment> sqlParts = new ArrayList<>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            SQLFragment sql = new SQLFragment("DROP COLUMN ");
            if (prop.getExactName())
            {
                sql.append(quoteIdentifier(prop.getName()));
            }
            else
            {
                sql.appendIdentifier(makePropertyIdentifier(prop.getName()));
            }
            sqlParts.add(sql);
        }

        SQLFragment f = new SQLFragment("ALTER TABLE ");
        f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
        f.append(" ").append(sqlParts, ", ");
        return f;
    }

    // TODO if there are cases where user-defined columns need indices, this method will need to support
    // creating indices like getCreateTableStatement does.
    private List<SQLFragment> getAddColumnsStatements(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        String pkColumn = null;
        Constraint constraint = null;

        List<SQLFragment> columnSpecs = new ArrayList<>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            columnSpecs.add(getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
                constraint = new Constraint(change.getTableName(), Constraint.CONSTRAINT_TYPES.PRIMARYKEY, false, null);
            }
        }

        SQLFragment alter = new SQLFragment("ALTER TABLE ");
        alter.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
        alter.append(" ");
        String sep = "";
        for (SQLFragment col : columnSpecs)
        {
            alter.append(sep);
            alter.append("ADD COLUMN ");
            alter.append(col);
            sep = ", ";
        }
        statements.add(alter);
        if (null != pkColumn)
        {
            SQLFragment addPk = new SQLFragment("ALTER TABLE ");
            addPk.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            addPk.append(" ADD CONSTRAINT ").appendIdentifier(constraint.getName())
                .append(" ").append(constraint.getType().toString()).append(" (")
                .appendIdentifier(makePropertyIdentifier(pkColumn)).append(")");
            statements.add(addPk);
        }

        return statements;
    }

    private List<SQLFragment> getDropConstraintsStatement(TableChange change)
    {
        return change.getConstraints().stream().map(constraint -> {
            SQLFragment f = new SQLFragment("ALTER TABLE ");
            f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            f.append(" DROP CONSTRAINT ").appendIdentifier(constraint.getName());
            return f;
        }).collect(Collectors.toList());
    }

    private List<SQLFragment> getAddConstraintsStatement(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        Collection<Constraint> constraints = change.getConstraints();

        if (null!=constraints && !constraints.isEmpty())
        {
            statements = constraints.stream().map(constraint -> {
                List<SQLFragment> columns = new ArrayList<>();
                for (String col : constraint.getColumns())
                {
                    columns.add(new SQLFragment().appendIdentifier(col));
                }

                SQLFragment f = new SQLFragment();
                f.append("DO $$\nBEGIN\nIF NOT EXISTS\n(SELECT 1 FROM information_schema.constraint_column_usage\nWHERE table_name = ")
                    .appendStringLiteral(change.getSchemaName() + "." + change.getTableName(), this)
                    .append(" and constraint_name = ")
                    .appendStringLiteral(constraint.getName(), this)
                    .append(") THEN\nALTER TABLE ");
                f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                f.append(" ADD CONSTRAINT ").appendIdentifier(constraint.getName()).append(" ")
                    .append(constraint.getType().toString()).append(" (")
                    .append(columns, ",")
                    .append(")").appendEOS().append("\nEND IF)").appendEOS().append("\nEND$$").appendEOS();
                return f;
            }).collect(Collectors.toList());
        }

        return statements;
    }

    private List<SQLFragment> getCreateTableStatements(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        List<SQLFragment> createTableSqlParts = new ArrayList<>();
        String pkColumn = null;
        for (PropertyStorageSpec prop : change.getColumns())
        {
            createTableSqlParts.add(getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
            }
        }

        for (PropertyStorageSpec.ForeignKey foreignKey : change.getForeignKeys())
        {
            DbSchema schema = DbSchema.get(foreignKey.getSchemaName(), DbSchemaType.Module);
            TableInfo tableInfo = foreignKey.isProvisioned() ?
                foreignKey.getTableInfoProvisioned() :
                schema.getTable(foreignKey.getTableName());
            String constraintName = "fk_" + foreignKey.getColumnName() + "_" + change.getTableName() + "_" + tableInfo.getName();
            SQLFragment fkFrag = new SQLFragment("CONSTRAINT ");
            fkFrag.appendIdentifier(constraintName)
                .append(" FOREIGN KEY (")
                .appendIdentifier(makePropertyIdentifier(foreignKey.getColumnName()))
                .append(") REFERENCES ")
                .appendIdentifier(tableInfo.getSchema().getName()).append(".").appendIdentifier(tableInfo.getName())
                .append(" (")
                .appendIdentifier(makePropertyIdentifier(foreignKey.getForeignColumnName()))
                .append(")");
            createTableSqlParts.add(fkFrag);
        }

        SQLFragment create = new SQLFragment("CREATE TABLE ");
        create.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
        create.append(" (").append(createTableSqlParts, ", ").append(")");
        statements.add(create);
        if (null != pkColumn)
        {
            // Making this just for consistent naming
            Constraint constraint = new Constraint(change.getTableName(), Constraint.CONSTRAINT_TYPES.PRIMARYKEY, false, null);

            SQLFragment addPk = new SQLFragment("ALTER TABLE ");
            addPk.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            addPk.append(" ADD CONSTRAINT ").appendIdentifier(constraint.getName())
                .append(" ").append(constraint.getType().toString()).append(" (")
                .appendIdentifier(makePropertyIdentifier(pkColumn)).append(")");
            statements.add(addPk);
        }

        statements.addAll(getCreateIndexStatements(change));
        statements.addAll(getAddConstraintsStatement(change));
        return statements;
    }

    private List<SQLFragment> getCreateIndexStatements(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        for (PropertyStorageSpec.Index index : change.getIndexedColumns())
        {
            String newIndexName = nameIndex(change.getTableName(), index.columnNames);
            SQLFragment f = new SQLFragment("CREATE ");
            if (index.isUnique)
                f.append("UNIQUE ");
            f.append("INDEX ").appendIdentifier(newIndexName).append(" ON ");
            f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            f.append(" (");
            String separator = "";
            for (String columnName : index.columnNames)
            {
                f.append(separator).appendIdentifier(makePropertyIdentifier(columnName));
                separator = ", ";
            }
            f.append(")");
            f.appendEOS();
            statements.add(f);

            if (index.isClustered)
            {
                SQLFragment c = new SQLFragment();
                c.append(PropertyStorageSpec.CLUSTER_TYPE.CLUSTER.toString()).append(" ");
                c.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                c.append(" USING ").appendIdentifier(newIndexName);
                statements.add(c);
            }
        }
        return statements;
    }

    @Override
    public String nameIndex(String tableName, String[] indexedColumns)
    {
        return AliasManager.makeLegalName(tableName + '_' + StringUtils.join(indexedColumns, "_"), this);
    }

    private SQLFragment getSqlColumnSpec(PropertyStorageSpec prop)
    {
        return getSqlColumnSpec(prop, prop.getName());
    }

    private SQLFragment getSqlColumnSpec(PropertyStorageSpec prop, String columnName)
    {
        SQLFragment colSpec = new SQLFragment();
        colSpec.appendIdentifier(makePropertyIdentifier(columnName)).append(" ");
        colSpec.append(getSqlTypeName(prop));

        // Apply size and precision to varchar and Decimal types
        if (prop.getJdbcType() == JdbcType.VARCHAR && prop.getSize() != -1 && prop.getSize() <= SqlDialect.MAX_VARCHAR_SIZE)
        {
            colSpec.append("(").append(prop.getSize().toString()).append(")");
        }
        else if (prop.getJdbcType() == JdbcType.DECIMAL)
        {
            colSpec.append(DEFAULT_DECIMAL_SCALE_PRECISION);
        }

        if (prop.isPrimaryKey() || !prop.isNullable())
            colSpec.append(" NOT NULL");

        if (null != prop.getDefaultValue())
        {
            if (prop.getJdbcType() == JdbcType.BOOLEAN)
            {
                colSpec.append(" DEFAULT ");
                colSpec.append((Boolean)prop.getDefaultValue() ? getBooleanTRUE() : getBooleanFALSE());
            }
            else if (prop.getJdbcType() == JdbcType.VARCHAR)
            {
                colSpec.append(" DEFAULT ");
                colSpec.append(getStringHandler().quoteStringLiteral(prop.getDefaultValue().toString()));
            }
            else
            {
                throw new IllegalArgumentException("Default value on type " + prop.getJdbcType().name() + " is not supported.");
            }
        }
        return colSpec;
    }

    @Override
    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        try
        {
            trackTempTables(createdTableNames);
        }
        catch (SQLException e)
        {
            LOG.warn("error cleaning up temp schema", e);
        }

        DbSchema coreSchema = CoreSchema.getInstance().getSchema();
        SqlExecutor executor = new SqlExecutor(coreSchema);

        //rs = conn.getMetaData().getFunctions(dbName, tempSchemaName, "%");

        new SqlSelector(coreSchema, "SELECT proname AS SPECIFIC_NAME, CAST(proargtypes AS VARCHAR) FROM pg_proc WHERE pronamespace=(select oid from pg_namespace where nspname = ?)", DbSchema.getTemp().getName()).forEach(
            new ForEachBlock<>()
            {
                private Map<String, String> _types = null;

                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    if (null == _types)
                    {
                        _types = new HashMap<>();
                        new SqlSelector(coreSchema, "SELECT CAST(oid AS VARCHAR) as oid, typname, (select nspname from pg_namespace where oid = typnamespace) as nspname FROM pg_type").forEach(type ->
                            _types.put(type.getString(1), quoteIdentifier(type.getString(3)) + "." + quoteIdentifier(type.getString(2))));
                    }

                    String name = rs.getString(1);
                    String[] oids = StringUtils.split(rs.getString(2), ' ');
                    SQLFragment drop = new SQLFragment("DROP FUNCTION temp.").append(name);
                    drop.append("(");
                    String comma = "";
                    for (String oid : oids)
                    {
                        drop.append(comma).append(_types.get(oid));
                        comma = ",";
                    }
                    drop.append(")");

                    try
                    {
                        executor.execute(drop);
                    }
                    catch (BadSqlGrammarException x)
                    {
                        LOG.warn("could not clean up postgres function : temp." + name, x);
                    }
                }
            });

        // TODO delete types in temp schema as well! search for "CREATE TYPE" in StatementUtils.java
    }

    //
    // ARRAY and SET syntax
    //

    // NOTE LabKey currently does not support ARRAY[VARCHAR], use ARRAY[text] instead
    //
    // Postgres string literals can be auto-cast to both VARCHAR and TEXT.  These all work
    //    'color' = 'color'::varchar
    //    'color' = 'color'::text
    //     ARRAY['color'] = ARRAY['color'::text];
    // However, ARRAY[text] cannot be auto cast to ARRAY[varchar]
    //     ARRAY['color'] = ARRAY['color'::varchar];    -- ERROR!
    //


    @Override
    public boolean supportsArrays()
    {
        return true;
    }

    @Override
    public SQLFragment array_construct(SQLFragment[] elements)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("ARRAY[");
        String separator = "";
        for (SQLFragment element : elements)
        {
            ret.append(separator);
            ret.append(element);
            separator = ", ";
        }
        ret.append("]");
        return ret;
    }

    @Override
    public SQLFragment array_all_in_array(SQLFragment a, SQLFragment b)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("(").append(a).append(") <@ (").append(b).append(")");
        return ret;
    }

    @Override
    public SQLFragment array_some_in_array(SQLFragment a, SQLFragment b)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("(").append(a).append(") && (").append(b).append(")");
        return ret;
    }

    @Override
    public SQLFragment array_none_in_array(SQLFragment a, SQLFragment b)
    {
        return new SQLFragment(" NOT (").append(array_some_in_array(a, b)).append(")");
    }

    @Override
    public SQLFragment array_same_array(SQLFragment a, SQLFragment b)
    {
        SQLFragment ret = new SQLFragment();
        ret.append(array_all_in_array(a, b)).append(" AND ").append(array_all_in_array(b, a));
        return ret;
    }

    @Override
    public SQLFragment array_not_same_array(SQLFragment a, SQLFragment b)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("NOT (").append(array_all_in_array(a, b)).append(") OR NOT (").append(array_all_in_array(b, a)).append(")");
        return ret;
    }

    @Override
    public SQLFragment element_in_array(SQLFragment a, SQLFragment b)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("(").append(a).append(")");
        // DOCs imply that IS NOT DISTINCT FROM ANY should work, but it doesn't???
        // ret.append(" IS NOT DISTINCT FROM ANY(");
        ret.append(" = ANY(");
        ret.append(b);
        ret.append(")");
        return ret;
    }

    @Override
    public SQLFragment element_not_in_array(SQLFragment a, SQLFragment b)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("(").append(a).append(")");
        // DOCs imply that IS NOT DISTINCT FROM ANY should work, but it doesn't???
        // ret.append(" IS DISTINCT FROM ALL(");
        ret.append(" <> ALL(");
        ret.append(b);
        ret.append(")");
        return ret;
    }
}
