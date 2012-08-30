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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.InClauseGenerator;
import org.labkey.api.data.ParameterMarkerInClauseGenerator;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 8/19/12
 * Time: 3:33 PM
 */

// Note: Use this only with a servlet container & scope that support JDBC4.
public class ArrayParameterInClauseGenerator implements InClauseGenerator
{
    private final static boolean ENABLED = false;
    private final DbScope _scope;
    private final InClauseGenerator _parameterMarkerGenerator = new ParameterMarkerInClauseGenerator();

    public ArrayParameterInClauseGenerator(DbScope scope)
    {
        _scope = scope;
    }

    // TODO:
    // - Trial call to createArrayOf()
    // - Move createArrayOf() to Parameter
    // - Try using underlying connection to call createArrayOf()

    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Object[] params)
    {
        SqlDialect dialect = _scope.getSqlDialect();

        // Fall back on parameter marker approach  // TODO: Increase params.length check to 10? 100?
        if (params.length < 1 || !dialect.isSqlArrayCompatible(params) || !ENABLED)
            return _parameterMarkerGenerator.appendInClauseSql(sql, params);

        Array array = null;
        Connection conn = null;

        try
        {
            conn = _scope.getConnection();
            String typeName = StringUtils.lowerCase(dialect.getSqlTypeNameFromObject(params[0]));
            array = conn.createArrayOf(typeName, params);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            _scope.releaseConnection(conn);
        }

        sql.append(" = ANY (?)");
        sql.add(array);

        return sql;
    }
}
