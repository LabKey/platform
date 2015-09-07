/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.InClauseGenerator;
import org.labkey.api.data.ParameterMarkerInClauseGenerator;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;

import java.util.Collection;

/**
 * Note: Use this only with a servlet container & scope that support JDBC4.
 * User: adam
 * Date: 8/19/12
 * Time: 3:33 PM
 */
public class ArrayParameterInClauseGenerator implements InClauseGenerator
{
    private final DbScope _scope;
    private final InClauseGenerator _parameterMarkerGenerator = new ParameterMarkerInClauseGenerator();

    public ArrayParameterInClauseGenerator(DbScope scope)
    {
        _scope = scope;
    }

    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        SqlDialect dialect = _scope.getSqlDialect();

        if (params.size() < 2 || !isSqlArrayCompatible(dialect, params))
        {
            // Fall back on parameter marker approach
            return _parameterMarkerGenerator.appendInClauseSql(sql, params);
        }

        sql.append(" = ANY (?)");
        sql.add(params);

        return sql;
    }

    /**
     * Return true if this array can be converted to a JDBC SQL Array type. We must have a SQL type name for the
     * class and all elements must have the same class.
     */
    private boolean isSqlArrayCompatible(SqlDialect dialect, Collection<?> elements)
    {
        Object firstElement = elements.iterator().next();
        String typeName = dialect.getSqlTypeNameFromObject(firstElement);

        if (null == typeName)
            return false;

        Class firstParamClass = firstElement.getClass();

        for (Object param : elements)
            if (param.getClass() != firstParamClass)
                return false;

        return true;
    }
}
