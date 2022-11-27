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

import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.dialect.StatementWrapper;

import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * User: adam
 * Date: 8/11/2015
 * Time: 1:19 PM
 */
public class MicrosoftSqlServer2016Dialect extends MicrosoftSqlServer2014Dialect
{
    @Override
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt)
    {
        return new TimestampStatementWrapper(conn, stmt);
    }

    @Override
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        return new TimestampStatementWrapper(conn, stmt, sql);
    }

    /**
     * Per the SQL Server JDBC driver docs at <a href="https://docs.microsoft.com/en-us/sql/connect/jdbc/using-basic-data-types?view=sql-server-ver16">...</a>
     *
     * Note that java.sql.Timestamp values can no longer be used to compare values from a datetime column starting
     * from SQL Server 2016. This limitation is due to a server-side change that converts datetime to datetime2
     * differently, resulting in non-equitable values. The workaround to this issue is to either change datetime
     * columns to datetime2(3), use String instead of java.sql.Timestamp, or change database compatibility level
     * to 120 or below.
     *
     *
     * java.sql.Timestamp.toString() includes the nanos in a ISO 8061-like format
     */
    private static class TimestampStatementWrapper extends StatementWrapper
    {
        public TimestampStatementWrapper(ConnectionWrapper conn, Statement stmt)
        {
            super(conn, stmt);
        }

        public TimestampStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
        {
            super(conn, stmt, sql);
        }

        @Override
        public void setTimestamp(String parameterName, Timestamp x)
                throws SQLException
        {
            if (x != null)
            {
                setObject(parameterName, x.toString());
            }
            else
            {
                super.setTimestamp(parameterName, x);
            }
        }

        @Override
        public void setTimestamp(String parameterName, Timestamp x, Calendar cal)
                throws SQLException
        {
            if (x != null)
            {
                setObject(parameterName, x.toString());
            }
            else
            {
                super.setTimestamp(parameterName, x, cal);
            }
        }

        @Override
        public void setTimestamp(int parameterIndex, Timestamp x)
                throws SQLException
        {
            if (x != null)
            {
                setObject(parameterIndex, x.toString());
            }
            else
            {
                super.setTimestamp(parameterIndex, x);
            }
        }

        @Override
        public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal)
                throws SQLException
        {
            if (x != null)
            {
                setObject(parameterIndex, x.toString());
            }
            else
            {
                super.setTimestamp(parameterIndex, x, cal);
            }
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType, int scale)
                throws SQLException
        {
            x = x instanceof Timestamp ? x.toString() : x;
            super.setObject(parameterIndex, x, targetSqlType, scale);
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType)
                throws SQLException
        {
            x = x instanceof Timestamp ? x.toString() : x;
            super.setObject(parameterIndex, x, targetSqlType);
        }

        @Override
        public void setObject(int parameterIndex, Object x)
                throws SQLException
        {
            x = x instanceof Timestamp ? x.toString() : x;
            super.setObject(parameterIndex, x);
        }

        @Override
        public void setObject(String parameterName, Object x, int targetSqlType, int scale)
                throws SQLException
        {
            x = x instanceof Timestamp ? x.toString() : x;
            super.setObject(parameterName, x, targetSqlType, scale);
        }

        @Override
        public void setObject(String parameterName, Object x, int targetSqlType)
                throws SQLException
        {
            x = x instanceof Timestamp ? x.toString() : x;
            super.setObject(parameterName, x, targetSqlType);
        }

        @Override
        public void setObject(String parameterName, Object x)
                throws SQLException
        {
            x = x instanceof Timestamp ? x.toString() : x;
            super.setObject(parameterName, x);
        }


    }
}
