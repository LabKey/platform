/*
 * Copyright (c) 2011 LabKey Corporation
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
    private static final String TABLE_NAME_REGEX;
    private static final String STATEMENT_ENDING_REGEX;
    private static final String COMMENT_REGEX = "((/\\*.+?\\*/)|(^--.+?$))\\s*";   // Single-line or block comment, followed by white space

    static
    {
        if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
        {
            TABLE_NAME_REGEX = "((?:(?:\\w+)\\.)?(?:#?\\w+))";  // # allows for temp table names
            STATEMENT_ENDING_REGEX = "((; GO$)|(;$)|( GO$))\\s*";       // Semicolon, GO, or both
        }
        else
        {
            TABLE_NAME_REGEX = "((?:(?:\\w+)\\.)?(?:\\w+))";
            STATEMENT_ENDING_REGEX = ";$(\\s*)";
        }
    }

    private final Map<String, Collection<String>> _statements = new LinkedHashMap<String, Collection<String>>();
    private final List<String> _endingStatements = new LinkedList<String>();

    private String _contents;
    private int _row = 0;

    ScriptReorderer(String contents)
    {
        _contents = contents;
    }

    public String getReorderedScript(boolean isHtml)
    {
        List<SqlPattern> patterns = new LinkedList<SqlPattern>();

        patterns.add(new SqlPattern("INSERT (?:INTO )?" + TABLE_NAME_REGEX + " \\([^\\)]+?\\) VALUES \\([^\\)]+?\\)\\s*(" + STATEMENT_ENDING_REGEX + "|$(\\s*))", Type.Table, RowEffect.Insert));
        patterns.add(new SqlPattern("INSERT (?:INTO )?" + TABLE_NAME_REGEX + " \\([^\\)]+?\\) SELECT .+?"+ STATEMENT_ENDING_REGEX, Type.Table, RowEffect.Insert));
        patterns.add(new SqlPattern(getRegExWithPrefix("INSERT INTO "), Type.Table, RowEffect.Insert));

        patterns.add(new SqlPattern(getRegExWithPrefix("UPDATE "), Type.Table, RowEffect.Alter));
        patterns.add(new SqlPattern(getRegExWithPrefix("DELETE FROM "), Type.Table, RowEffect.Alter));

        patterns.add(new SqlPattern("CREATE (?:UNIQUE )?(?:CLUSTERED )?INDEX \\w+? ON " + TABLE_NAME_REGEX + ".+?" + STATEMENT_ENDING_REGEX, Type.Table, RowEffect.None));
        patterns.add(new SqlPattern(getRegExWithPrefix("CREATE TABLE "), Type.Table, RowEffect.None));
        patterns.add(new SqlPattern(getRegExWithPrefix("ALTER TABLE "), Type.Table, RowEffect.None));
        patterns.add(new SqlPattern(getRegExWithPrefix("DROP INDEX "), Type.Table, RowEffect.None));    // By convention, index names start with their associated table names

        if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
        {
            patterns.add(new SqlPattern(getRegExWithPrefix("DROP TABLE "), Type.Table, RowEffect.None));
            patterns.add(new SqlPattern(getRegExWithPrefix("CREATE TABLE "), Type.Table, RowEffect.None));
            patterns.add(new SqlPattern("(?:EXEC )?sp_rename (?:@objname\\s*=\\s*)?'" + TABLE_NAME_REGEX + ".*?'.+?" + STATEMENT_ENDING_REGEX, Type.Table, RowEffect.None));
            patterns.add(new SqlPattern("EXEC core\\.fn_dropifexists '(\\w+)', '(\\w+)'.+?" + STATEMENT_ENDING_REGEX, Type.Table, RowEffect.None));

            patterns.add(new SqlPattern("CREATE PROCEDURE .+?" + STATEMENT_ENDING_REGEX, Type.NonTable, RowEffect.None));
        }
        else
        {
            patterns.add(new SqlPattern(getRegExWithPrefix("DROP TABLE (?:IF EXISTS )?"), Type.Table, RowEffect.None));
            patterns.add(new SqlPattern(getRegExWithPrefix("CREATE (?:TEMPORARY )?TABLE "), Type.Table, RowEffect.None));
            patterns.add(new SqlPattern("SELECT core\\.fn_dropifexists\\s*\\('(\\w+)', '(\\w+)'.+?" + STATEMENT_ENDING_REGEX, Type.Table, RowEffect.None));
            patterns.add(new SqlPattern("SELECT SETVAL\\('([a-zA-Z]+)\\.([a-zA-Z]+)_.+?" + STATEMENT_ENDING_REGEX, Type.Table, RowEffect.None));
            patterns.add(new SqlPattern(getRegExWithPrefix("CLUSTER \\w+ ON "), Type.Table, RowEffect.None));   // e.g. CLUSTER PK_Keyword ON flow.Keyword
            patterns.add(new SqlPattern(getRegExWithPrefix("CLUSTER "), Type.Table, RowEffect.None));
            patterns.add(new SqlPattern(getRegExWithPrefix("ANALYZE "), Type.Table, RowEffect.None));

            patterns.add(new SqlPattern("CREATE FUNCTION .+? RETURNS \\w+ AS (.+?) (?:.+?) \\1 LANGUAGE plpgsql" + STATEMENT_ENDING_REGEX, Type.NonTable, RowEffect.None));
        }

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
                        tableName = m.group(2) + "." + m.group(1);

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
            appendStatement(newScript, "\n=======================\n", isHtml);

            for (String unknownStatement : _endingStatements)
                appendStatement(newScript, unknownStatement, isHtml);
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

        Collection<String> tableStatements = _statements.get(key);

        if (null == tableStatements)
        {
            tableStatements = new LinkedList<String>();
            _statements.put(key, tableStatements);
        }

        tableStatements.add(statement);
    }

    private void appendAllStatements(StringBuilder sb, boolean html)
    {
        for (Map.Entry<String, Collection<String>> tableStatements : _statements.entrySet())
            for (String statement : tableStatements.getValue())
                appendStatement(sb, statement, html);
    }

    private void appendStatement(StringBuilder sb, String statement, boolean html)
    {
        if (html)
        {
            sb.append("<tr class=\"");
            sb.append(0 == (_row % 2) ? "labkey-row" : "labkey-alternate-row");
            sb.append("\"><td>");
            sb.append(PageFlowUtil.filter(statement, true));
            sb.append("</td></tr>\n");
            _row++;
        }
        else
        {
            sb.append(statement);
        }
    }

    private enum Type {Table, NonTable}
    private enum RowEffect {None, Alter, Insert}
    //private enum Bar {}

    private static class SqlPattern
    {
        private final Pattern _pattern;
        private final Type _type;
        private RowEffect _rowEffect;

        private SqlPattern(String regex, Type type, RowEffect rowEffect)
        {
            _pattern = compile(regex);
            _type = type;
            _rowEffect = rowEffect;
        }

        private Pattern compile(String regEx)
        {
            return Pattern.compile(regEx.replaceAll(" ", "\\\\s+"), Pattern.CASE_INSENSITIVE + Pattern.DOTALL + Pattern.MULTILINE);
        }

        public RowEffect getRowEffect()
        {
            return _rowEffect;
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
}
