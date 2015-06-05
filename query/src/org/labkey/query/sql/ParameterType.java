/*
 * Copyright (c) 2011-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;

/**
 * User: matthewb
 * Date: Jan 3, 2011
 *
 * Used in query parameter parsing.
*/
public enum ParameterType
{
    BIGINT(JdbcType.BIGINT),
    BIT(JdbcType.BOOLEAN),
    CHAR(JdbcType.CHAR),
    DECIMAL(JdbcType.DECIMAL),
    DOUBLE(JdbcType.DOUBLE),
    FLOAT(JdbcType.DOUBLE),
    INTEGER(JdbcType.INTEGER),
    LONGVARCHAR(JdbcType.LONGVARCHAR),
    NUMERIC(JdbcType.DECIMAL),
    REAL(JdbcType.REAL),
    SMALLINT(JdbcType.SMALLINT),
//    DATE(JdbcType.DATE),
//    TIME(JdbcType.TIME),
    TIMESTAMP(JdbcType.TIMESTAMP),
    TINYINT(JdbcType.TINYINT),
    VARCHAR(JdbcType.VARCHAR)
    ;

    final JdbcType type;

    ParameterType(JdbcType type)
    {
        this.type = type;
    }

    public static ParameterType resolve(@NotNull String s)
    {
        try
        {
            return ParameterType.valueOf(s.toUpperCase());
        }
        catch (IllegalArgumentException x)
        {
            return null;
        }
    }

    public Object convert(Object o)
    {
        return type.convert(o);
    }
}
