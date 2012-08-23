package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * User: adam
 * Date: 8/7/12
 * Time: 9:37 PM
 */
public class ParameterMarkerInClauseGenerator implements InClauseGenerator
{
    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        sql.append("IN (");
        String questionMarks = StringUtils.repeat("?, ", params.size());
        sql.append(questionMarks.substring(0, questionMarks.length() - 2));
        sql.append(")");

        sql.addAll(params);

        return sql;
    }
}
