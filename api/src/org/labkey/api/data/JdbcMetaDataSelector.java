/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 7/10/2014
 * Time: 8:52 AM
 */

/**
 * This class is used to read JDBC meta data via the standard DatabaseMetaData methods. It follows the basic Selector
 * pattern, but much simpler to keep it out of that class hierarchy.
 */
public class JdbcMetaDataSelector
{
    private final JdbcMetaDataLocator _locator;
    private final JdbcMetaDataResultSetFactory _factory;

    public JdbcMetaDataSelector(JdbcMetaDataLocator locator, JdbcMetaDataResultSetFactory factory)
    {
        _locator = locator;
        _factory = factory;
    }

    private static final int DEADLOCK_RETRIES = 5;

    public TableResultSet getResultSet() throws SQLException
    {
        return handleResultSet(new ResultSetHandler<ResultSetImpl>()
        {
            @Override
            public ResultSetImpl handle(ResultSet rs) throws SQLException
            {
                return new ResultSetImpl(rs, QueryLogging.emptyQueryLogging());
            }

            @Override
            public boolean shouldClose()
            {
                return false;
            }
        });
    }

    public void forEach(final ForEachBlock<ResultSet> block) throws SQLException
    {
        handleResultSet(new ResultSetHandler<Object>()
        {
            @Override
            public Object handle(ResultSet rs) throws SQLException
            {
                try
                {
                    while (rs.next())
                        block.exec(rs);
                }
                catch (StopIteratingException sie)
                {
                }

                return null;
            }

            @Override
            public boolean shouldClose()
            {
                return true;
            }
        });
    }

    private <T> T handleResultSet(ResultSetHandler<T> handler) throws SQLException
    {
        // Retry on deadlock, up to five times, see #22148 and #15640.
        int tries = 1;
        boolean success = false;
        ResultSet rs = null;

        while (true)
        {
            try
            {
                rs = _factory.getResultSet(_locator.getDatabaseMetaData(), _locator);
                T ret = handler.handle(rs);
                success = true;
                return ret;
            }
            catch (SQLException e)
            {
                String message = e.getMessage();

                // Retry on deadlock, up to five times, see #22607, #22148 and #15640.
                if (null == message || !message.contains("deadlocked") || tries++ >= DEADLOCK_RETRIES)
                    throw e;
            }
            finally
            {
                if ((handler.shouldClose() || !success) && null != rs)
                    rs.close();
            }
        }
    }

    private interface ResultSetHandler<T>
    {
        T handle(ResultSet rs) throws SQLException;
        boolean shouldClose();
    }

    public interface JdbcMetaDataResultSetFactory
    {
        ResultSet getResultSet(DatabaseMetaData dbmd, JdbcMetaDataLocator locator) throws SQLException;
    }
}
