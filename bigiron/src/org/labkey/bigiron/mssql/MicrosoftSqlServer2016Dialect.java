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

import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.logging.LogHelper;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class MicrosoftSqlServer2016Dialect extends MicrosoftSqlServer2014Dialect
{
    private static final Logger LOG = LogHelper.getLogger(MicrosoftSqlServer2016Dialect.class, "SQL Server settings");

    private volatile String _language = null;
    private volatile String _dateFormat = null;
    private volatile DateTimeFormatter _timestampFormatter = null;

    @Override
    public void prepare(DbScope scope)
    {
        super.prepare(scope);

        Map<String, Object> map = new SqlSelector(scope, "SELECT language, date_format FROM sys.dm_exec_sessions WHERE session_id = @@spid").getMap();
        _language = (String) map.get("language");
        _dateFormat = (String) map.get("date_format");

        // This seems to be the only string format acceptable for sending Timestamps, but unfortunately it's ambiguous;
        // SQL Server interprets the "MM-dd" portion based on the database's regional settings. So we must query the
        // current date format and switch the formatter pattern based on what we find. See Issue 51129.
        String mdFormat = switch (_dateFormat)
        {
            case "mdy" -> "MM-dd";
            case "dmy" -> "dd-MM";
            default -> throw new IllegalStateException("Unsupported date format: " + _dateFormat);
        };

        _timestampFormatter = DateTimeFormatter.ofPattern("yyyy-" + mdFormat + " HH:mm:ss.SSS");

        LOG.info("\n    Language:                 {}\n    DateFormat:               {}", _language, _dateFormat);
    }

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
     * "Note that java.sql.Timestamp values can no longer be used to compare values from a datetime column starting
     * from SQL Server 2016. This limitation is due to a server-side change that converts datetime to datetime2
     * differently, resulting in non-equitable values. The workaround to this issue is to either change datetime
     * columns to datetime2(3), use String instead of java.sql.Timestamp, or change database compatibility level
     * to 120 or below." We can't change column types in external schemas, and we don't want a low compatibility level,
     * so we send Timestamps as Strings. SQL Server is very picky about this format; for example, Timestamp.toString(),
     * which is basically ISO, is actually ambiguous and fails if language is French (e.g.). See Issue 51129.
     */
    private class TimestampStatementWrapper extends StatementWrapper
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
        public void setTimestamp(String parameterName, Timestamp x) throws SQLException
        {
            if (x != null)
            {
                setObject(parameterName, convert(x));
            }
            else
            {
                super.setTimestamp(parameterName, x);
            }
        }

        @Override
        public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException
        {
            if (x != null)
            {
                setObject(parameterName, convert(x));
            }
            else
            {
                super.setTimestamp(parameterName, x, cal);
            }
        }

        @Override
        public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException
        {
            if (x != null)
            {
                setObject(parameterIndex, convert(x));
            }
            else
            {
                super.setTimestamp(parameterIndex, x);
            }
        }

        @Override
        public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException
        {
            if (x != null)
            {
                setObject(parameterIndex, convert(x));
            }
            else
            {
                super.setTimestamp(parameterIndex, x, cal);
            }
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws SQLException
        {
            if (targetSqlType == Types.TIMESTAMP)
                setObject(parameterIndex, x);
            else
                super.setObject(parameterIndex, x, targetSqlType, scale);
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException
        {
            if (targetSqlType == Types.TIMESTAMP)
                setObject(parameterIndex, x);
            else
                super.setObject(parameterIndex, x, targetSqlType);
        }

        @Override
        public void setObject(int parameterIndex, Object x) throws SQLException
        {
            super.setObject(parameterIndex, convert(x));
        }

        @Override
        public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException
        {
            if (targetSqlType == Types.TIMESTAMP)
                setObject(parameterName, x);
            else
                super.setObject(parameterName, x, targetSqlType, scale);
        }

        @Override
        public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException
        {
            if (targetSqlType == Types.TIMESTAMP)
                setObject(parameterName, x);
            else
                super.setObject(parameterName, x, targetSqlType);
        }

        @Override
        public void setObject(String parameterName, Object x) throws SQLException
        {
            super.setObject(parameterName, convert(x));
        }

        private Object convert(Object x)
        {
            return x instanceof Timestamp ts ? convert(ts) : x;
        }

        private String convert(Timestamp ts)
        {
            return _timestampFormatter.format(ts.toInstant().atZone(ZoneId.systemDefault()));
        }
    }

    public static class TestCase
    {
        @Test
        public void testTimestamps()
        {
            DbScope scope = DbScope.getLabKeyScope();
            SqlDialect dialect = scope.getSqlDialect();

            if (dialect.isSqlServer() && dialect instanceof MicrosoftSqlServer2016Dialect)
            {
                try (Connection conn = DbScope.getLabKeyScope().getConnection())
                {
                    Timestamp ts = new Timestamp(new Date().getTime());
                    Calendar cal = Calendar.getInstance();

                    try (PreparedStatement statement = conn.prepareStatement("SELECT ?"))
                    {
                        Assert.assertTrue(statement instanceof TimestampStatementWrapper);
                        statement.setTimestamp(1, ts);
                        statement.setTimestamp(1, ts, cal);
                        statement.setObject(1, ts, Types.TIMESTAMP, 0);
                        statement.setObject(1, ts, Types.TIMESTAMP);
                        statement.setObject(1, ts);
                    }

                    if (ModuleLoader.getInstance().hasModule("DataIntegration"))
                    {
                        try (CallableStatement statement = conn.prepareCall("{call etltest.etlTest(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}"))
                        {
                            Assert.assertTrue(statement instanceof TimestampStatementWrapper);
                            statement.setTimestamp("filterStartTimeStamp", ts);
                            statement.setTimestamp("filterStartTimeStamp", ts, cal);
                            statement.setObject("filterStartTimeStamp", ts, Types.TIMESTAMP, 0);
                            statement.setObject("filterStartTimeStamp", ts, Types.TIMESTAMP);
                            statement.setObject("filterStartTimeStamp", ts);
                        }
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
