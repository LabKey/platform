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

import jakarta.servlet.ServletException;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.logging.LogHelper;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Set;

public class MicrosoftSqlServer2016Dialect extends MicrosoftSqlServer2014Dialect
{
    private static final Logger LOG = LogHelper.getLogger(MicrosoftSqlServer2016Dialect.class, "SQL Server settings");

    private volatile String _language = null;
    private volatile String _dateFormat = null;
    private volatile FastDateFormat _timestampFormatter = null;

    @Override
    public void prepare(DbScope scope)
    {
        super.prepare(scope);

        try
        {
            LanguageSettings settings = getLanguageSettings(scope, scope.getConnection());
            _language = settings.getLanguage();
            _dateFormat = settings.getDate_format();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        // This seems to be the only string format acceptable for sending Timestamps, but unfortunately it's ambiguous;
        // SQL Server interprets the "MM-dd" portion based on the database's regional settings. So we must query the
        // current date format and switch the formatter pattern based on what we find. See Issue 51129.
        String mdFormat = switch (_dateFormat)
        {
            case "mdy" -> "MM-dd";
            case "dmy" -> "dd-MM";
            default -> throw new IllegalStateException("Unsupported date format: " + _dateFormat);
        };

        _timestampFormatter = FastDateFormat.getInstance("yyyy-" + mdFormat + " HH:mm:ss.SSS");

        LOG.info("\n    Language:                 {}\n    DateFormat:               {}", _language, _dateFormat);
    }

    // TODO: Turn this into a record on 24.11 (24.7 SqlSelector doesn't support records)
    public static class LanguageSettings
    {
        String _language;
        String _date_format;

        public String getLanguage()
        {
            return _language;
        }

        public void setLanguage(String language)
        {
            _language = language;
        }

        public String getDate_format()
        {
            return _date_format;
        }

        public void setDate_format(String date_format)
        {
            _date_format = date_format;
        }

        @Override
        public String toString()
        {
            return "LanguageSettings{" +
                    "_language='" + _language + '\'' +
                    ", _date_format='" + _date_format + '\'' +
                    '}';
        }
    }

    private static LanguageSettings getLanguageSettings(DbScope scope, Connection conn)
    {
        return new SqlSelector(scope, conn, "SELECT language, date_format FROM sys.dm_exec_sessions WHERE session_id = @@spid")
            .getObject(LanguageSettings.class);
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
    class TimestampStatementWrapper extends StatementWrapper
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
            if (targetSqlType == Types.TIMESTAMP && x instanceof Timestamp)
                setObject(parameterIndex, x);
            else
                super.setObject(parameterIndex, x, targetSqlType, scale);
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException
        {
            if (targetSqlType == Types.TIMESTAMP && x instanceof Timestamp)
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
            if (targetSqlType == Types.TIMESTAMP && x instanceof Timestamp)
                setObject(parameterName, x);
            else
                super.setObject(parameterName, x, targetSqlType, scale);
        }

        @Override
        public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException
        {
            if (targetSqlType == Types.TIMESTAMP && x instanceof Timestamp)
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
            return _timestampFormatter.format(ts);
        }
    }

    public static class TestCase
    {
        @Test
        public void testTimestamps()
        {
            DbScope scope = DbScope.getLabKeyScope();
            SqlDialect dialect = scope.getSqlDialect();

            if (dialect.isSqlServer() && dialect instanceof MicrosoftSqlServer2016Dialect ms2016Dialect)
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

                    if (conn instanceof ConnectionWrapper cw)
                    {
                        // Test a few timestamp conversions. Need to accommodate mdy vs. dmy databases.
                        TimestampStatementWrapper wrapper = ms2016Dialect.new TimestampStatementWrapper(cw, null);
                        test(wrapper, "mdy".equals(ms2016Dialect._dateFormat) ? "1800-05-10 10:32:00.000" : "1800-10-05 10:32:00.000", "1800-05-10 10:32:00");
                        test(wrapper, "mdy".equals(ms2016Dialect._dateFormat) ? "1800-05-10 10:32:00.647" : "1800-10-05 10:32:00.647", "1800-05-10 10:32:00.647");
                        test(wrapper, "2024-09-09 20:26:14.841", "2024-09-09 20:26:14.841");
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }

        private void test(TimestampStatementWrapper wrapper, String expected, String test)
        {
            Timestamp ts = Timestamp.valueOf(test);
            Assert.assertEquals(expected, wrapper.convert(ts));
        }

        @Test
        public void testCompareClauses() throws SQLException, ServletException
        {
            // Issue 51472 pointed out issues with Timestamp conversions on French SQL Server. Primary fixes were in
            // the DateCompareClause subclasses, so put them through their paces here.

            DbScope labKeyScope = DbScope.getLabKeyScope();
            // Clone the LabKey scope so it has its own SqlDialect that we can prepare every time we set the language
            TestScope scope = new TestScope(labKeyScope);

            TableInfo containers = CoreSchema.getInstance().getTableInfoContainers();
            ColumnInfo created = containers.getColumn("Created");

            try (Connection conn = scope.getConnection())
            {
                setLanguage(scope, conn, "English");
                testMultipleFilters(conn, containers, created.getFieldKey());

                if (scope.getSqlDialect().isSqlServer())
                {
                    setLanguage(scope, conn, "French");
                    testMultipleFilters(conn, containers, created.getFieldKey());
                }
            }

            // Null out connection to prevent query profiler from holding onto it via this scope
            scope.clearConnection();
        }

        private static class TestScope extends DbScope
        {
            private Connection _connection = getWrapped();

            public TestScope(DbScope scope) throws ServletException, SQLException
            {
                super(scope.getDataSourceName(), scope.getLabKeyDataSource());
            }

            @Override
            public Connection getConnection()
            {
                return _connection;
            }

            private Connection getWrapped() throws SQLException
            {
                // Hand out an un-pooled connection since we might set language and don't want that to persist outside this test
                return new ConnectionWrapper(getUnpooledConnection(), this, null, DbScope.ConnectionType.Transaction, null);
            }

            private void clearConnection()
            {
                _connection = null;
            }
        }

        private void setLanguage(DbScope scope, Connection conn, String language)
        {
            SqlDialect dialect = scope.getSqlDialect();
            if (dialect.isSqlServer())
            {
                new SqlExecutor(scope, conn).execute("SET LANGUAGE " + language);
                dialect.prepare(scope);
                LOG.info(getLanguageSettings(DbScope.getLabKeyScope(), conn));
            }
        }

        private void testMultipleFilters(Connection conn, TableInfo table, FieldKey date)
        {
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DATE, -30);
            Date startDate = cal.getTime();

            testFilter(conn, table, date, startDate, CompareType.DATE_EQUAL);
            testFilter(conn, table, date, startDate, CompareType.DATE_NOT_EQUAL);
            testFilter(conn, table, date, startDate, CompareType.DATE_GTE);
            testFilter(conn, table, date, startDate, CompareType.DATE_GT);
            testFilter(conn, table, date, startDate, CompareType.DATE_LT);
            testFilter(conn, table, date, startDate, CompareType.DATE_LTE);
        }

        // We don't care about the row counts, just that each query executes without any exceptions
        private void testFilter(Connection conn, TableInfo table, FieldKey fk, Object value, CompareType type)
        {
            SimpleFilter filter = new SimpleFilter(fk, value, type);

            new TestTableSelector(table, conn, Collections.singleton(table.getColumn(fk)), filter, null).getRowCount();

            // This mimics the query that UserManager.getActiveDaysCount() generates
            SQLFragment sql = new SQLFragment("SELECT * FROM (SELECT CAST(")
                .append(fk.getName())
                .append(" AS DATE) AS ")
                .append(fk.getName())
                .append(" FROM ")
                .append(table.getSelectName())
                .append(") x ")
                .append(filter.getSQLFragment(table.getSqlDialect()));

            new SqlSelector(table.getSchema().getScope(), conn, sql).getRowCount();
        }

        private static class TestTableSelector extends TableSelector
        {
            public TestTableSelector(@NotNull TableInfo table, @NotNull Connection conn, Set<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
            {
                super(table, conn, columns, filter, sort, true);
            }
        }
    }
}
