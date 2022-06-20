/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.bigiron.mssql;

import org.apache.commons.lang3.time.FastDateFormat;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.dialect.TableResolver;

import java.sql.Timestamp;

/**
 * User: adam
 * Date: 8/11/2015
 * Time: 1:19 PM
 */
public class MicrosoftSqlServer2016Dialect extends MicrosoftSqlServer2014Dialect
{
    private static final FastDateFormat TIMESTAMP_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");

    public MicrosoftSqlServer2016Dialect(TableResolver tableResolver)
    {
        super(tableResolver);
    }

    @Override
    public Object translateJdbcParameterValue(DbScope scope, Object value)
    {
        // Per the SQL Server JDBC driver docs at https://docs.microsoft.com/en-us/sql/connect/jdbc/using-basic-data-types?view=sql-server-ver16

        // Note that java.sql.Timestamp values can no longer be used to compare values from a datetime column starting
        // from SQL Server 2016. This limitation is due to a server-side change that converts datetime to datetime2
        // differently, resulting in non-equitable values. The workaround to this issue is to either change datetime
        // columns to datetime2(3), use String instead of java.sql.Timestamp, or change database compatibility level
        // to 120 or below.

        if (value instanceof Timestamp ts)
        {
            return TIMESTAMP_FORMAT.format(ts);
        }
        return value;
    }
}
