/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.query.sql;

import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;

import java.util.Map;

/**
 * Suggestions for common standard-SQL constructs that LabKey SQL does not support. Authors (and AI assistants)
 * regularly reach for window functions, OFFSET, EXTRACT, ILIKE, etc.; the generic "Syntax error near '...'" gives
 * them no way to converge on working LabKey SQL, so we append a targeted hint when the failure looks recognizable.
 *
 * These hints are consulted ONLY after a parse error has already occurred, keyed off the token ANTLR blames (with a
 * little look-behind for constructs where the blamed token is generic). None of the trigger words are reserved in
 * LabKey SQL -- most are legal identifiers -- so this class must never influence what parses; message text only.
 */
final class SyntaxHints
{
    private SyntaxHints()
    {
    }

    @Nullable
    static String forSyntaxError(RecognitionException re, @Nullable CommonTokenStream tokens)
    {
        if (null == re.token || null == re.token.getText())
            return null;
        String near = re.token.getText().toLowerCase();
        String prev1 = previous(tokens, re.token, 1);
        String prev2 = previous(tokens, re.token, 2);
        String prev3 = previous(tokens, re.token, 3);

        switch (near)
        {
            case "offset":
                return "OFFSET is not supported. Use LIMIT n; apply paging via the client API (maxRows/offset).";
            case "fetch":
                return "FETCH FIRST is not supported. Use LIMIT n.";
            case "ilike":
                return "ILIKE is not supported. Use LOWER(x) LIKE LOWER(pattern).";
            case "using":
                return "JOIN ... USING is not supported. Use JOIN ... ON a.col = b.col.";
            case "nulls":
                return "NULLS FIRST/LAST is not supported. Try ORDER BY x IS NULL, x.";
            case ":":
                return "The '::' cast syntax is not supported. Use CAST(expr AS TYPE).";
            case "(":
                // "MAX(a) OVER (...)" parses OVER as a column alias, so the '(' gets the blame
                if ("over".equals(prev1))
                    return "Window functions (OVER) are not supported in LabKey SQL.";
                if ("filter".equals(prev1))
                    return "FILTER is not supported. Use an aggregate over CASE: SUM(CASE WHEN condition THEN 1 ELSE 0 END).";
                // "JOIN S USING (x)" parses USING as the table alias, so the '(' gets the blame
                if ("using".equals(prev1))
                    return "JOIN ... USING is not supported. Use JOIN ... ON a.col = b.col.";
                return null;
            case "distinct":
                if ("is".equals(prev1) || "not".equals(prev1))
                    return "IS [NOT] DISTINCT FROM is not supported. Use is_distinct_from(a, b) or is_not_distinct_from(a, b).";
                if ("(".equals(prev1))
                    return "DISTINCT is only supported inside COUNT() and GROUP_CONCAT().";
                return null;
            case "from":
                // "EXTRACT(YEAR FROM d)" parses as a method call, so the FROM gets the blame
                if ("(".equals(prev2) && "extract".equals(prev3))
                    return "EXTRACT is not supported. Use YEAR(), MONTH(), DAYOFMONTH(), HOUR(), etc.";
                return null;
            default:
                if (near.startsWith("'") && "interval".equals(prev1))
                    return "INTERVAL literals are not supported. Use TIMESTAMPADD('SQL_TSI_DAY', n, ts) and TIMESTAMPDIFF().";
                // "SELECT TOP 10 a FROM R" parses TOP as an expression, so the blame lands on a later token
                if (("top".equals(prev1) && "select".equals(prev2)) || ("top".equals(prev2) && "select".equals(prev3)))
                    return "TOP is not supported. Use LIMIT n at the end of the statement.";
                return null;
        }
    }

    @Nullable
    private static String previous(@Nullable CommonTokenStream tokens, Token t, int back)
    {
        if (null == tokens)
            return null;
        int i = t.getTokenIndex();
        if (i < back || i >= tokens.size())
            return null;
        Token p = tokens.get(i - back);
        return null == p || null == p.getText() ? null : p.getText().toLowerCase();
    }

    // Suggestions for unrecognized method names, keyed by lower-cased name. These entries are valid on both
    // databases; dialect-specific suggestions live in forUnknownMethod(). Some entries (len, charindex, instr)
    // are dialect-specific methods that resolve on one database and land here on the other.
    private static final Map<String, String> methodHints = Map.ofEntries(
            Map.entry("position", "Use LOCATE(substring, string[, start])."),
            Map.entry("extract", "Use YEAR(), MONTH(), DAYOFMONTH(), HOUR(), etc."),
            Map.entry("string_agg", "Use GROUP_CONCAT([DISTINCT] expr[, separator])."),
            Map.entry("nvl", "Use COALESCE(a, b) or IFNULL(a, b)."),
            Map.entry("isnull", "Use IFNULL(a, b) or COALESCE(a, b)."),
            Map.entry("iif", "Use CASE WHEN condition THEN a ELSE b END."),
            Map.entry("if", "Use CASE WHEN condition THEN a ELSE b END."),
            Map.entry("datediff", "Use TIMESTAMPDIFF('SQL_TSI_DAY', ts1, ts2) or AGE()/age_in_days()."),
            Map.entry("dateadd", "Use TIMESTAMPADD('SQL_TSI_DAY', n, ts)."),
            Map.entry("date_part", "Use YEAR(), MONTH(), DAYOFMONTH(), HOUR(), etc."),
            Map.entry("day", "Use DAYOFMONTH(date)."),
            Map.entry("len", "Use LENGTH(string)."),
            Map.entry("instr", "Use LOCATE(substring, string)."),
            Map.entry("charindex", "Use LOCATE(substring, string)."),
            Map.entry("getdate", "Use NOW()."),
            Map.entry("sysdate", "Use NOW()."),
            Map.entry("current_date", "Use CURDATE()."),
            Map.entry("current_timestamp", "Use NOW()."),
            Map.entry("current_time", "Use CURTIME().")
    );

    /**
     * The suggestion should simply be appropriate for the current dialect -- never name a database product.
     * A null dialect (expression parsing, tests) gets the portable suggestion.
     */
    @Nullable
    static String forUnknownMethod(String name, @Nullable SqlDialect dialect)
    {
        boolean pg = null != dialect && dialect.isPostgreSQL();
        return switch (name.toLowerCase())
        {
            case "trim" -> pg ? "Use btrim(x) or LTRIM(RTRIM(x))." : "Use LTRIM(RTRIM(x)).";
            case "substring_index" -> pg ? "Use split_part(string, delimiter, n)." : null;
            case "regexp_like", "regexp_matches" -> pg ? "Use similar_to(x, pattern) or regexp_replace(x, pattern, replacement)." : "Use LIKE with wildcards.";
            case "date_trunc" -> pg ? "Use CAST(ts AS DATE) for day granularity, or to_char(ts, format)." : "Use CAST(ts AS DATE) for day granularity.";
            default -> methodHints.get(name.toLowerCase());
        };
    }
}
