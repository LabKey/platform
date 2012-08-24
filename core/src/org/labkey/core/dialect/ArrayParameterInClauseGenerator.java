/*
 * Copyright (c) 2012 LabKey Corporation
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

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * User: adam
 * Date: 8/19/12
 * Time: 3:33 PM
 */

// Note: Use this only with a servlet container & scope that support JDBC4.
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
        // Fall back on parameter marker approach
        if (!arrayCandidate(params))
            return _parameterMarkerGenerator.appendInClauseSql(sql, params);

        Array array = null;
        Connection conn = null;

        try
        {
            conn = _scope.getConnection();

            Object[] ints = new Object[params.size()];
            int i = 0;

            for (Object param : params)
            {
                ints[i++] = param;
            }

            array = conn.createArrayOf("int4", ints);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            try
            {
                if (null != conn)
                    conn.close();
            }
            catch (SQLException e)
            {
            }
        }

        sql.append(" = ANY (?)");
        sql.add(array);

        return sql;
    }

    private boolean arrayCandidate(Collection<?> params)
    {
        // TODO: Re-enable once we've added JDBC4 detection
        return false;
//        if (params.size() < 100)
//            return false;
//
//        Object firstParam = params.iterator().next();
//
//        if (!candidateType(firstParam))
//            return false;
//
//        Class firstParamClass = firstParam.getClass();
//
//        for (Object param : params)
//            if (param.getClass() != firstParamClass)
//                return false;
//
//        return true;
    }

    private boolean candidateType(Object param)
    {
        return (Integer.class.isAssignableFrom(param.getClass()));
    }
}
