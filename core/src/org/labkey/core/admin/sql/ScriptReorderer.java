/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

package org.labkey.core.admin.sql;

import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptReorderer
{
    public static final String COMMENT_REGEX = "((/\\*.+?\\*/)|(^[ \\t]*--.*?$))\\s*";   // Single-line or block comment, followed by white space

    private final List<Map<String, Collection<Statement>>> _statementLists = new LinkedList<>();
    private final List<String> _endingStatements = new LinkedList<>();
    private final Map<String, String> _constraintTables = CaseInsensitiveHashMap.of(); // Track tables associated with constraints

    private Map<String, Collection<Statement>> _currentStatements;

    private final DbSchema _schema;
    private final String SCHEMA_NAME_REGEX;
    private final String TABLE_NAME_REGEX;
    private final String TABLE_NAME2_REGEX;
    private final String TABLE_NAME_NO_UNDERSCORE_REGEX;
    private final String STATEMENT_ENDING_REGEX;
    private final String CONSTRAINT_NAME_REGEX;

    private String _contents;
    private int _row = 0;

    ScriptReorderer(DbSchema schema, String contents)
    {
        _schema = schema;
        _contents = contents;

        if (_schema.getSqlDialect().isSqlServer())
        {
            SCHEMA_NAME_REGEX = "(((\\w+)|(\\[\\w+\\]))\\.)?"; // optional [] around schema name
            TABLE_NAME_REGEX = "(?<table>" + SCHEMA_NAME_REGEX + "((#?\\w+)|(\\[#?\\w+\\])))";  // # allows for temp table names, optional [] around table name
            TABLE_NAME_NO_UNDERSCORE_REGEX = null;
            STATEMENT_ENDING_REGEX = "((; GO\\s*$)|(;\\s*$)|( GO\\s*$))\\s*";       // Semicolon, GO, or both
            CONSTRAINT_NAME_REGEX = "(?<constraint>((\\w+)|(\\[\\w+\\])))"; // optional [] around name
        }
        else
        {
            SCHEMA_NAME_REGEX = "((\\w+)\\.)?";
            TABLE_NAME_REGEX = "(?<table>" + SCHEMA_NAME_REGEX + "(\\w+))";
            TABLE_NAME_NO_UNDERSCORE_REGEX = "(?<table>" + SCHEMA_NAME_REGEX + "([[a-zA-Z0-9]]+))";
            STATEMENT_ENDING_REGEX = ";(\\s*?)((--)[^\\n]*)?$(\\s*)";
            CONSTRAINT_NAME_REGEX = "(?<constraint>(\\w+))";
        }

        TABLE_NAME2_REGEX = TABLE_NAME_REGEX.replace("table", "table2");

        newStatementList();
    }

    private void newStatementList()
    {
        _currentStatements = new LinkedHashMap<>();
        _statementLists.add(_currentStatements);
    }

    public String getReorderedScript(boolean isHtml)
    {
        List<SqlPattern> patterns = new LinkedList<>();

        patterns.add(new SqlPattern("INSERT (INTO )?" + TABLE_NAME_REGEX + " \\([^\\)]+?\\) VALUES \\([^\\)]+?\\)\\s*(" + STATEMENT_ENDING_REGEX + "|$(\\s*))", Type.Table, Operation.InsertRows));
        patterns.add(new SqlPattern("INSERT (INTO )?" + TABLE_NAME_REGEX + " \\([^\\)]+?\\) SELECT .+?"+ STATEMENT_ENDING_REGEX, Type.Table, Operation.InsertRows));
        patterns.add(new SqlPattern(getRegExWithPrefix("INSERT INTO "), Type.Table, Operation.InsertRows));

        patterns.add(new SqlPattern(getRegExWithPrefix("UPDATE (ON )?"), Type.Table, Operation.AlterRows));
        patterns.add(new SqlPattern(getRegExWithPrefix("DELETE FROM "), Type.Table, Operation.AlterRows));

        patterns.add(new SqlPattern("CREATE (UNIQUE )?((NON)?CLUSTERED )?INDEX (IF NOT EXISTS )?\\[?(\\w+?)\\]? ON " + TABLE_NAME_REGEX + ".+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
        patterns.add(new SqlPattern(getRegExWithPrefix("CREATE TABLE "), Type.Table, Operation.Other));
        patterns.add(new SqlPattern(getRegExWithPrefix("TRUNCATE( TABLE)? "), Type.Table, Operation.Other));

        patterns.add(new SqlPattern(getRegExWithPrefix("DROP TABLE (IF EXISTS )?"), Type.Table, Operation.Other));

        if (_schema.getSqlDialect().isSqlServer())
        {
            patterns.add(new SqlPattern(getRegExWithPrefix("CREATE TABLE "), Type.Table, Operation.Other));

            // Specific sp_rename pattern for table rename
            patterns.add(new SqlPattern("(EXEC(UTE)? )?sp_rename (@objname\\s*=\\s*)?'" + TABLE_NAME_REGEX + "'\\s*,\\s*'" + TABLE_NAME2_REGEX + "'" + STATEMENT_ENDING_REGEX, Type.Table, Operation.RenameTable));

            // All other sp_renames
            patterns.add(new SqlPattern("(EXEC(UTE)? )?sp_rename (@objname\\s*=\\s*)?'" + TABLE_NAME_REGEX + ".*?'.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
            patterns.add(new SqlPattern("EXEC(UTE)? core\\.fn_dropifexists\\s*(@objname\\s*=\\s*)?'(?<table>\\w+)'\\s*,\\s*(@objschema\\s*=\\s*)?'(?<schema>\\w+)'\\s*,\\s*(@objtype\\s*=\\s*)?'(TABLE|COLUMN|INDEX|DEFAULT|CONSTRAINT)'.*?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
            patterns.add(new SqlPattern("EXEC(UTE)? core\\.fn_dropifexists\\s*'(\\w+)'\\s*,\\s*'(?<schema>\\w+)'.*?" + STATEMENT_ENDING_REGEX, Type.NonTable, Operation.Other));

            // DROP INDEX on SQL Server follows a similar pattern to CREATE INDEX (above)
            patterns.add(new SqlPattern("DROP INDEX (IF EXISTS )?\\w+ ON " + TABLE_NAME_REGEX + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));

            patterns.add(new SqlPattern("(CREATE|ALTER) PROCEDURE .+?" + STATEMENT_ENDING_REGEX, Type.NonTable, Operation.Other));
            patterns.add(new SqlPattern("ALTER TABLE " + TABLE_NAME_REGEX + " CHECK CONSTRAINT " + CONSTRAINT_NAME_REGEX + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
        }
        else
        {
            patterns.add(new SqlPattern("ALTER TABLE " + TABLE_NAME_REGEX + " RENAME TO " + TABLE_NAME2_REGEX + STATEMENT_ENDING_REGEX, Type.Table, Operation.RenameTable));
            patterns.add(new SqlPattern(getRegExWithPrefix("CREATE (TEMPORARY )?TABLE "), Type.Table, Operation.Other));
            patterns.add(new SqlPattern("SELECT core\\.fn_dropifexists\\s*\\('(?<table>\\w+)'\\s*,\\s*'(?<schema>\\w+)'\\s*,\\s*'(TABLE|COLUMN|INDEX|DEFAULT|CONSTRAINT)'.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
            patterns.add(new SqlPattern("SELECT core\\.fn_dropifexists\\s*\\('(\\w+)'\\s*,\\s*'(?<schema>\\w+)'.+?" + STATEMENT_ENDING_REGEX, Type.NonTable, Operation.Other));
            patterns.add(new SqlPattern("SELECT SETVAL\\('" + TABLE_NAME_NO_UNDERSCORE_REGEX + "_.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
            patterns.add(new SqlPattern(getRegExWithPrefix("CLUSTER \\w+ ON "), Type.Table, Operation.Other));   // e.g. CLUSTER PK_Keyword ON flow.Keyword
            patterns.add(new SqlPattern(getRegExWithPrefix("CLUSTER "), Type.Table, Operation.Other));
            patterns.add(new SqlPattern(getRegExWithPrefix("ANALYZE "), Type.Table, Operation.Other));

            // Can't prefix index names with table name on PostgreSQL... find table name based on our naming conventions.
            patterns.add(new SqlPattern("(DROP|ALTER) INDEX (IF EXISTS )?" + SCHEMA_NAME_REGEX + "(IX_|IDX_|UQ_)" + TABLE_NAME_REGEX + "_.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));

            // Find table name based on sequence naming conventions
            patterns.add(new SqlPattern("CREATE SEQUENCE " + TABLE_NAME_REGEX + "_.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));

            patterns.add(new SqlPattern("CREATE (OR REPLACE )?FUNCTION .+? RETURNS \\w+ AS (\\S+) (.+?) \\2 LANGUAGE (plpgsql|SQL)( STRICT)?( IMMUTABLE)?( VOLATILE)?( COST \\d+)?" + STATEMENT_ENDING_REGEX, Type.NonTable, Operation.Other));
            patterns.add(new SqlPattern(getRegExWithPrefix("COMMENT ON TABLE "), Type.Table, Operation.Other));
            patterns.add(new SqlPattern("DO (\\S+) (.+?) END \\1" + STATEMENT_ENDING_REGEX, Type.NonTable, Operation.Other));
        }

        patterns.add(new SqlPattern("ALTER TABLE " + TABLE_NAME_REGEX + " (WITH CHECK )?ADD CONSTRAINT " + CONSTRAINT_NAME_REGEX + " FOREIGN KEY\\s*\\([^\\)]+?\\) REFERENCES " + TABLE_NAME2_REGEX + " \\([^\\)]+?\\).*?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
        // Put this at the end to capture all other ALTER TABLE statements (i.e., not RENAMEs)
        patterns.add(new SqlPattern(getRegExWithPrefix("ALTER TABLE (IF EXISTS )?(ONLY )?"), Type.Table, Operation.Other));

        Pattern commentPattern = compile(COMMENT_REGEX);

        StringBuilder newScript = new StringBuilder();
        StringBuilder unrecognized = new StringBuilder();

        boolean firstMatch = true;

        while (!_contents.isEmpty())
        {
            // Parse all the comments first. If we match a table statement next, we'll include the comments.
            StringBuilder comments = new StringBuilder();

            Matcher m = commentPattern.matcher(_contents);

            while (m.lookingAt())
            {
                comments.append(m.group());
                _contents = _contents.substring(m.end());
                m = commentPattern.matcher(_contents);
            }

            boolean recognized = false;

            // Look for table statements that we recognize
            for (SqlPattern pattern : patterns)
            {
                if (pattern.getType() == Type.NonTable)
                    continue;

                m = pattern.getMatcher(_contents);

                if (m.lookingAt())
                {
                    if (firstMatch)
                    {
                        // Section before first match (copyright, license, type creation, etc.) always goes first
                        addStatement("initial section", null, unrecognized.toString());
                        unrecognized = new StringBuilder();
                        firstMatch = false;
                    }

                    String tableName = m.group("table");

                    if (-1 == tableName.indexOf('.'))
                    {
                        String schemaName;

                        // Does pattern have a "schema" named capturing group? If so, use it, otherwise default to current schema.
                        if (m.pattern().pattern().contains("(?<schema>"))
                            schemaName = m.group("schema");
                        else
                            schemaName = _schema.getName();

                        tableName = schemaName + "." + tableName;
                    }

                    String tableName2 = null;

                    if (m.pattern().pattern().contains("(?<table2>"))
                    {
                        tableName2 = m.group("table2");
                        assert tableName2 != null;
                    }

                    if (pattern.getOperation() == Operation.RenameTable)
                    {
                        assert null != tableName2;

                        // Associate the rename statement with the new table name
                        tableName = _schema.getName() + "." + tableName2;
                        tableName2 = null;

                        // Since any future references to the old name will actually refer to a new table, we don't want to intermingle
                        // the previous statements with subsequent statements. Create a demarcation point by moving to a new,
                        // empty statement list that will contain the rename and all subsequent statements.
                        newStatementList();
                    }

                    String tableKey = addStatement(tableName, tableName2, comments + m.group());

                    if (m.pattern().pattern().contains("(?<constraint>"))
                    {
                        String constraintName = m.group("constraint");
                        assert constraintName != null;
                        String constraintKey = normalizeName(constraintName);
                        if (!_constraintTables.containsKey(constraintKey))
                        {
                            _constraintTables.put(constraintKey, tableKey);
                        }
                    }

                    _contents = _contents.substring(m.end());
                    recognized = true;
                    break;
                }
            }

            String nonTableStatement = null;

            // Now look for non-table statements that we recognize
            if (!recognized)
            {
                for (SqlPattern pattern : patterns)
                {
                    if (pattern.getType() == Type.Table)
                        continue;

                    m = pattern.getMatcher(_contents);

                    if (m.lookingAt())
                    {
                        nonTableStatement = comments + m.group();
                        _contents = _contents.substring(m.end());
                        recognized = true;
                        break;
                    }
                }
            }

            // If we recognize the current statement then append previously parsed unknown statements to the end
            if (recognized)
            {
                if (!unrecognized.isEmpty())
                {
                    _endingStatements.add(unrecognized.toString());
                    unrecognized = new StringBuilder();
                }
            }
            else
            {
                unrecognized.append(comments);

                if (!_contents.isEmpty())
                {
                    unrecognized.append(_contents.charAt(0));
                    _contents = _contents.substring(1);    // Advance a single character and we'll try again
                }
            }

            if (null != nonTableStatement)
                _endingStatements.add(nonTableStatement);
        }

        // Add any remaining unrecognized statements
        if (!unrecognized.isEmpty())
            _endingStatements.add(unrecognized.toString());

        // Uncomment this code to list all the detected table names, which can help debug the table/schema parsing patterns
//        for (Map<String, Collection<Statement>> statementList : _statementLists)
//        {
//            if (isHtml)
//                newScript.append("<tr><td>");
//            for (Map.Entry<String, Collection<Statement>> tableStatements : statementList.entrySet())
//            {
//                newScript.append(tableStatements.getKey());
//
//                if (isHtml)
//                    newScript.append("<br>\n");
//                else
//                    newScript.append("\n");
//            }
//            if (isHtml)
//                newScript.append("</td></tr>");
//        }

        appendAllStatements(newScript, isHtml);

        if (!_endingStatements.isEmpty())
        {
            appendStatement(newScript, new Statement(null, "\n=======================\n"), isHtml);

            for (String unknownStatement : _endingStatements)
                appendStatement(newScript, new Statement(null, unknownStatement), isHtml);
        }

        return newScript.toString();
    }

    private String getRegExWithPrefix(String prefix)
    {
        return prefix + TABLE_NAME_REGEX + ".*?" + STATEMENT_ENDING_REGEX;
    }

    private Pattern compile(String regEx)
    {
        return Pattern.compile(regEx.replaceAll(" ", "\\\\s+"), Pattern.CASE_INSENSITIVE + Pattern.DOTALL + Pattern.MULTILINE);
    }

    // Return table key that's associated with this statement
    private String addStatement(String tableName, @Nullable String tableName2, String statement)
    {
        // Remove brackets for map key
        String key = normalizeName(tableName);
        String key2 = normalizeName(tableName2);

        // If there's a second table in the statement that's referenced later in the script then associate the statement
        // with the second table. For example, an FK definition will end up after BOTH tables have been created.
        if (null != key2 && index(key2) > index(key))
        {
            tableName = tableName2;
            key = key2;
        }

        Collection<Statement> tableStatements = _currentStatements.computeIfAbsent(key, k -> new LinkedList<>());

        tableStatements.add(new Statement(tableName, statement));

        return key;
    }

    // Remove brackets and lower case
    private @Nullable String normalizeName(@Nullable String name)
    {
        return name != null ? name.replace("[", "").replace("]", "").toLowerCase() : null;
    }

    private int index(String tableName)
    {
        int i = 0;
        String key = tableName.toLowerCase();

        for (String name: _currentStatements.keySet())
        {
            if (name.equals(key))
                return i;

            i++;
        }

        return -1;  // Table has not been referenced yet in this script
    }

    private void appendAllStatements(StringBuilder sb, boolean html)
    {
        for (Map<String, Collection<Statement>> statementList : _statementLists)
            for (Map.Entry<String, Collection<Statement>> tableStatements : statementList.entrySet())
                for (Statement statement : tableStatements.getValue())
                    appendStatement(sb, statement, html);
    }

    private void appendStatement(StringBuilder sb, Statement statement, boolean html)
    {
        if (html)
        {
            sb.append("<tr class=\"");
            sb.append(0 == (_row % 2) ? "labkey-row" : "labkey-alternate-row");
            sb.append("\"><td>");
            appendStatement(sb, statement);
            sb.append("</td></tr>\n");
            _row++;
        }
        else
        {
            sb.append(statement.sql());
        }
    }

    private void appendStatement(StringBuilder sb, Statement statement)
    {
        String sql = PageFlowUtil.filter(statement.sql(), true);
        String tableName = statement.tableName();

        // If we have a table name then try to highlight the first occurrence in statement
        if (null != tableName)
        {
            String schemaName = null;
            boolean containsTableName = Strings.CI.contains(sql, tableName);

            if (!containsTableName && tableName.contains("."))
            {
                String[] parts = tableName.split("\\.");
                tableName = parts[0];
                schemaName = parts[1];
                containsTableName = Strings.CI.contains(sql, tableName);
            }

            if (containsTableName)
            {
                sql = StringUtilsLabKey.replaceFirstIgnoreCase(sql, tableName, "<b>" + tableName + "</b>");
                if (null != schemaName)
                    sql = StringUtilsLabKey.replaceFirstIgnoreCase(sql, schemaName, "<b>" + schemaName + "</b>");
            }
        }

        sb.append(sql);
    }

    private enum Type {Table, NonTable}
    private enum Operation {Other, AlterRows, InsertRows, RenameTable}

    private static class SqlPattern
    {
        private final Pattern _pattern;
        private final Type _type;
        private final Operation _operation;

        private SqlPattern(String regex, Type type, Operation operation)
        {
            _pattern = compile(regex);
            _type = type;
            _operation = operation;
        }

        private Pattern compile(String regEx)
        {
            return Pattern.compile(regEx.replaceAll(" ", "\\\\s+"), Pattern.CASE_INSENSITIVE + Pattern.DOTALL + Pattern.MULTILINE);
        }

        public Operation getOperation()
        {
            return _operation;
        }

        public Type getType()
        {
            return _type;
        }

        public Matcher getMatcher(CharSequence input)
        {
            return _pattern.matcher(input);
        }
    }

    // Saving the original table name helps with highlighting, especially in the case of a table rename
    private record Statement(@Nullable String tableName, String sql) {}
}
