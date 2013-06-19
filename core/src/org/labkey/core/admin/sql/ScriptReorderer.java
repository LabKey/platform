/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ScriptReorderer
{
    private static final String SCHEMA_NAME_REGEX = "(?:(?:\\w+)\\.)?";
    private static final String TABLE_NAME_REGEX;
    private static final String STATEMENT_ENDING_REGEX;
    private static final String COMMENT_REGEX = "((/\\*.+?\\*/)|(^--.+?$))\\s*";   // Single-line or block comment, followed by white space

    static
    {
        if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
        {
            TABLE_NAME_REGEX = "(" + SCHEMA_NAME_REGEX + "(?:#?\\w+))";  // # allows for temp table names
            STATEMENT_ENDING_REGEX = "((; GO\\s*$)|(;\\s*$)|( GO\\s*$))\\s*";       // Semicolon, GO, or both
        }
        else
        {
            TABLE_NAME_REGEX = "(" + SCHEMA_NAME_REGEX + "(?:\\w+))";
            STATEMENT_ENDING_REGEX = ";(\\s*?)((--)[^\\n]*)?$(\\s*)";
        }
    }

    private final List<Map<String, Collection<Statement>>> _statementLists = new LinkedList<>();
    private final List<String> _endingStatements = new LinkedList<>();

    private Map<String, Collection<Statement>> _currentStatements;

    private final String _schemaName;
    private String _contents;
    private int _row = 0;

    ScriptReorderer(String schemaName, String contents)
    {
        _schemaName = schemaName;
        _contents = contents;
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

        patterns.add(new SqlPattern("INSERT (?:INTO )?" + TABLE_NAME_REGEX + " \\([^\\)]+?\\) VALUES \\([^\\)]+?\\)\\s*(" + STATEMENT_ENDING_REGEX + "|$(\\s*))", Type.Table, Operation.InsertRows));
        patterns.add(new SqlPattern("INSERT (?:INTO )?" + TABLE_NAME_REGEX + " \\([^\\)]+?\\) SELECT .+?"+ STATEMENT_ENDING_REGEX, Type.Table, Operation.InsertRows));
        patterns.add(new SqlPattern(getRegExWithPrefix("INSERT INTO "), Type.Table, Operation.InsertRows));

        patterns.add(new SqlPattern(getRegExWithPrefix("UPDATE "), Type.Table, Operation.AlterRows));
        patterns.add(new SqlPattern(getRegExWithPrefix("DELETE FROM "), Type.Table, Operation.AlterRows));

        patterns.add(new SqlPattern("CREATE (?:UNIQUE )?(?:CLUSTERED )?INDEX \\w+? ON " + TABLE_NAME_REGEX + ".+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
        patterns.add(new SqlPattern(getRegExWithPrefix("CREATE TABLE "), Type.Table, Operation.Other));

        if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
        {
            patterns.add(new SqlPattern(getRegExWithPrefix("DROP TABLE "), Type.Table, Operation.Other));
            patterns.add(new SqlPattern(getRegExWithPrefix("CREATE TABLE "), Type.Table, Operation.Other));

            // Specific sp_rename pattern for table rename
            patterns.add(new SqlPattern("(?:EXEC )?sp_rename (?:@objname\\s*=\\s*)?'" + TABLE_NAME_REGEX + "', '" + TABLE_NAME_REGEX + "'" + STATEMENT_ENDING_REGEX, Type.Table, Operation.RenameTable));

            // All other sp_renames
            patterns.add(new SqlPattern("(?:EXEC )?sp_rename (?:@objname\\s*=\\s*)?'" + TABLE_NAME_REGEX + ".*?'.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
            patterns.add(new SqlPattern("EXEC core\\.fn_dropifexists '(\\w+)', '(\\w+)'.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));

            // Index names are prefixed with their associated table names on SQL Server
            patterns.add(new SqlPattern(getRegExWithPrefix("DROP INDEX "), Type.Table, Operation.Other));

            patterns.add(new SqlPattern("CREATE PROCEDURE .+?" + STATEMENT_ENDING_REGEX, Type.NonTable, Operation.Other));
        }
        else
        {
            patterns.add(new SqlPattern("ALTER TABLE " + TABLE_NAME_REGEX + " RENAME TO " + TABLE_NAME_REGEX + STATEMENT_ENDING_REGEX, Type.Table, Operation.RenameTable));
            patterns.add(new SqlPattern(getRegExWithPrefix("DROP TABLE (?:IF EXISTS )?"), Type.Table, Operation.Other));
            patterns.add(new SqlPattern(getRegExWithPrefix("CREATE (?:TEMPORARY )?TABLE "), Type.Table, Operation.Other));
            patterns.add(new SqlPattern("SELECT core\\.fn_dropifexists\\s*\\('(\\w+)', '(\\w+)'.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
            patterns.add(new SqlPattern("SELECT SETVAL\\('([a-zA-Z]+)\\.([a-zA-Z]+)_.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));
            patterns.add(new SqlPattern(getRegExWithPrefix("CLUSTER \\w+ ON "), Type.Table, Operation.Other));   // e.g. CLUSTER PK_Keyword ON flow.Keyword
            patterns.add(new SqlPattern(getRegExWithPrefix("CLUSTER "), Type.Table, Operation.Other));
            patterns.add(new SqlPattern(getRegExWithPrefix("ANALYZE "), Type.Table, Operation.Other));

            // Can't prefix index names with table name on PostgreSQL... find table name based on our naming conventions.
            patterns.add(new SqlPattern("(?:DROP|ALTER) INDEX " + SCHEMA_NAME_REGEX + "(?:IX_|IDX_)" + TABLE_NAME_REGEX + "_.+?" + STATEMENT_ENDING_REGEX, Type.Table, Operation.Other));

            patterns.add(new SqlPattern("CREATE FUNCTION .+? RETURNS \\w+ AS (.+?) (?:.+?) \\1 LANGUAGE plpgsql" + STATEMENT_ENDING_REGEX, Type.NonTable, Operation.Other));
        }

        // Put this at the end to catch all other ALTER TABLE statements (i.e., not RENAMEs)
        patterns.add(new SqlPattern(getRegExWithPrefix("ALTER TABLE "), Type.Table, Operation.Other));

        Pattern commentPattern = compile(COMMENT_REGEX);

        StringBuilder newScript = new StringBuilder();
        StringBuilder unrecognized = new StringBuilder();

        boolean firstMatch = true;

        while (0 < _contents.length())
        {
            // Parse all the comments first.  If we match a table statement next, we'll include the comments.
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
                        addStatement("initial section", unrecognized.toString());
                        unrecognized = new StringBuilder();
                        firstMatch = false;
                    }

                    String tableName = m.group(1);

                    if (-1 == tableName.indexOf('.'))
                    {
                        String schemaName = m.group(2).isEmpty() ? _schemaName : m.group(2);
                        tableName = schemaName + "." + m.group(1);
                    }

                    if (pattern.getOperation() == Operation.RenameTable)
                    {
                        // Associate the rename statement with the new table name
                        tableName = _schemaName + "." + m.group(2);

                        // Since future references to the old name will actual refer to a new table, we don't want to intermingle
                        // the previous statements with any subsequent statements. Create a demarkation point by moving to a new,
                        // empty statement list that will contain the rename and all subsequent statements.
                        newStatementList();
                    }

                    addStatement(tableName, comments + m.group());
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
                if (unrecognized.length() > 0)
                {
                    _endingStatements.add(unrecognized.toString());
                    unrecognized = new StringBuilder();
                }
            }
            else
            {
                unrecognized.append(comments);

                if (_contents.length() > 0)
                {
                    unrecognized.append(_contents.charAt(0));
                    _contents = _contents.substring(1);    // Advance a single character and we'll try again
                }
            }

            if (null != nonTableStatement)
                _endingStatements.add(nonTableStatement);
        }

        // Add any remaining unrecognized statements
        if (unrecognized.length() > 0)
            _endingStatements.add(unrecognized.toString());

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

    private void addStatement(String tableName, String statement)
    {
        String key = tableName.toLowerCase();

        Collection<Statement> tableStatements = _currentStatements.get(key);

        if (null == tableStatements)
        {
            tableStatements = new LinkedList<>();
            _currentStatements.put(key, tableStatements);
        }

        tableStatements.add(new Statement(tableName, statement));
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
            sb.append(statement.getSql());
        }
    }

    private void appendStatement(StringBuilder sb, Statement statement)
    {
        String sql = PageFlowUtil.filter(statement.getSql(), true);
        String tableName = statement.getTableName();

        // If we have a table name then try to highlight the first occurence in statement
        if (null != tableName)
        {
            int tableNameIndex = StringUtils.indexOfIgnoreCase(sql, tableName);

            if (-1 == tableNameIndex)
            {
                int dotIndex = tableName.indexOf('.');

                if (-1 != dotIndex)
                {
                    tableName = tableName.substring(dotIndex + 1);
                    tableNameIndex = StringUtils.indexOfIgnoreCase(sql, tableName);
                }
            }

            if (-1 != tableNameIndex)
            {
                sb.append(sql.substring(0, tableNameIndex));
                sb.append("<b>");
                sb.append(sql.substring(tableNameIndex, tableNameIndex + tableName.length()));
                sb.append("</b>");
                sb.append(sql.substring(tableNameIndex + tableName.length()));

                return;
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
        private Operation _operation;

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
    private static class Statement
    {
        private final @Nullable String _tableName;
        private final String _sql;

        private Statement(@Nullable String tableName, String sql)
        {
            _tableName = tableName;
            _sql = sql;
        }

        public @Nullable String getTableName()
        {
            return _tableName;
        }

        public String getSql()
        {
            return _sql;
        }
    }
}
