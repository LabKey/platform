package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: adam
 * Date: 8/7/12
 * Time: 9:37 PM
 */
public class ParameterMarkerInClauseGenerator implements InClauseGenerator
{
    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Object[] params, @Nullable ColumnInfo colInfo, String alias, boolean negated, boolean includeNull, boolean urlClause)
    {
        sql.append("((" + alias);
        sql.append(" " + (negated ? "NOT " : "") + "IN (");

        String questionMarks = StringUtils.repeat("?, ", params.length);
        sql.append(questionMarks.substring(0, questionMarks.length() - 2));

        sql.append(")");

        // TODO: Move this into SimpleFilter.InClause? Passing in converted params would eliminate need for colInfo and urlClause
        if (colInfo == null || !urlClause)
        {
            sql.addAll(params);
        }
        else
        {
            for (Object paramVal : params)
            {
                sql.add(CompareType.convertParamValue(colInfo, paramVal));
            }
        }

        if (includeNull)
        {
            if (negated)
                sql.append(") AND " + alias + " IS NOT NULL)");
            else
                sql.append(") OR " + alias + " IS NULL)");
        }
        else
        {
            if (negated)
                sql.append(") OR " + alias + " IS NULL)");
            else
                sql.append("))");
        }

        return sql;
    }
}
