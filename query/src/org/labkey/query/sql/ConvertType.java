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

import org.labkey.api.data.JdbcType;

/**
* represents type that can be used in a CAST or CONVERT function.
* 
* User: matthewb
* Date: Jan 3, 2011
* Time: 5:23:15 PM
*/
public enum ConvertType
{
    BIGINT(JdbcType.BIGINT),
    BINARY(JdbcType.BINARY),
    BIT(JdbcType.BOOLEAN),
    BOOLEAN(JdbcType.BOOLEAN),
    CHAR(JdbcType.CHAR),
    DECIMAL(JdbcType.DECIMAL),
    DOUBLE(JdbcType.DOUBLE),
    FLOAT(JdbcType.DOUBLE),
    GUID(JdbcType.VARCHAR),
    INTEGER(JdbcType.INTEGER),
    INTERVAL_MONTH(JdbcType.INTEGER),
    INTERVAL_YEAR(JdbcType.INTEGER),
    INTERVAL_DAY(JdbcType.INTEGER),
    INTERVAL_HOUR(JdbcType.INTEGER),
    INTERVAL_MINUTE(JdbcType.INTEGER),
    INTERVAL_SECOND(JdbcType.INTEGER),
    LONGVARBINARY(JdbcType.LONGVARBINARY),
    LONGVARCHAR(JdbcType.LONGVARCHAR),
    NUMERIC(JdbcType.DECIMAL),
    REAL(JdbcType.REAL),
    SMALLINT(JdbcType.SMALLINT),
    DATE(JdbcType.DATE),
    TIME(JdbcType.TIME),
    TIMESTAMP(JdbcType.TIMESTAMP),
    TINYINT(JdbcType.TINYINT),
    VARBINARY(JdbcType.VARBINARY),
    VARCHAR(JdbcType.VARCHAR)
     ;

    public final JdbcType jdbcType;
    
    ConvertType(JdbcType type)
    {
        this.jdbcType = type;
    }

    public Object convert(Object o)
    {
        return jdbcType.convert(o);
    }
}
