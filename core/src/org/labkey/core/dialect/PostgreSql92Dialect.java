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

import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.InClauseGenerator;
import org.labkey.api.data.ParameterMarkerInClauseGenerator;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableInClauseGenerator;
import org.labkey.api.data.dialect.BasePostgreSqlDialect;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.StandardJdbcHelper;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.template.Warnings;
import org.labkey.core.admin.sql.ScriptReorderer;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Override
    public String prepare(DbScope scope)
    {
        initializeInClauseGenerator(scope);
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

    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new StandardJdbcHelper(PostgreSqlDialectFactory.JDBC_PREFIX);
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

    private void initializeInClauseGenerator(DbScope scope)
    {
        _inClauseGenerator = getJdbcVersion(scope) >= 4 ? new ArrayParameterInClauseGenerator(scope) : new ParameterMarkerInClauseGenerator();
    }

    @Override
    public SQLFragment getAnalyzeCommandForTable(String tableName)
    {
        return new SQLFragment("ANALYZE ").appendIdentifier(tableName);
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
    // No need to split up PostgreSQL scripts; execute all statements in a single block (unless we have a special stored proc call).
    protected Pattern getSQLScriptSplitPattern()
    {
        return null;
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
}
