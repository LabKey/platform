/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 12/18/12
 * Time: 10:02 PM
 */
public class ResultSetSelector extends NonSqlExecutingSelector<ResultSetSelector>
{
    private final ResultSet _rs;
    private CompletionAction _completionAction = CompletionAction.Nothing;  // Default assumption is that caller closes the ResultSet (e.g., using try-with-resources)

    /*
        Note: By default, this class does not close the passed in ResultSet; caller must either close the ResultSet
        themselves or call setCompletionAction(Close).
    */

    public ResultSetSelector(DbScope scope, ResultSet rs, @Nullable Connection conn)
    {
        super(scope, conn);
        _rs = rs;
    }

    public ResultSetSelector(DbScope scope, ResultSet rs)
    {
        this(scope, rs, null);
    }

    @Override
    protected ResultSetSelector getThis()
    {
        return this;
    }

    @Override
    protected ResultSetFactory getStandardResultSetFactory()
    {
        return new ResultSetFactory() {
            @Override
            public ResultSet getResultSet(Connection conn) throws SQLException
            {
                return _rs;
            }

            @Override
            public boolean shouldClose()
            {
                return _completionAction.shouldClose();
            }

            @Override
            public void handleSqlException(SQLException e, @Nullable Connection conn)
            {
                throw getExceptionFramework().translate(getScope(), "ResultSetSelector", e);
            }
        };
    }

    @Override
    public TableResultSet getResultSet()
    {
        return new ResultSetImpl(_rs, QueryLogging.emptyQueryLogging());
    }

    @Override
    protected void afterComplete(ResultSet rs)
    {
        super.afterComplete(rs);

        try
        {
            _completionAction.afterComplete(rs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /*
        Tells the selector what action to take on the ResultSet after each operation is complete. Default is Nothing; the
        caller is responsible for closing the ResultSet (via try-with-resources, e.g.)
    */
    public ResultSetSelector setCompletionAction(CompletionAction action)
    {
        try
        {
            action.validate(_rs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        _completionAction = action;

        return this;
    }

    ResultSet getUnderlyingResultSet()
    {
        return _rs;
    }

    public enum CompletionAction
    {
        /*
            Close action means the passed in ResultSet will be closed after the first method is called. Unless you have
            a peculiar ResultSet (that continues to allow operations after close() is called), this action means that
            only one select method can be used on a ResultSet.
        */
        Close
        {
            @Override
            void validate(ResultSet rs)
            {
            }

            @Override
            boolean shouldClose()
            {
                return true;
            }

            @Override
            void afterComplete(ResultSet rs)
            {
            }
        },
        Nothing
        {
            @Override
            void validate(ResultSet rs)
            {
            }

            @Override
            boolean shouldClose()
            {
                return false;
            }

            @Override
            void afterComplete(ResultSet rs)
            {
            }
        },
        ScrollToTop
        {
            @Override
            void validate(ResultSet rs) throws SQLException
            {
                if (rs.getType() == ResultSet.TYPE_FORWARD_ONLY)
                    throw new IllegalStateException("Non-scrollable ResultSet can't be used with " + name());
            }

            @Override
            boolean shouldClose()
            {
                return false;
            }

            @Override
            void afterComplete(ResultSet rs) throws SQLException
            {
                rs.beforeFirst();
            }
        };

        // Should throw a RuntimeException if ResultSet is not appropriate for this action
        abstract void validate(ResultSet rs) throws SQLException;
        abstract boolean shouldClose();
        abstract void afterComplete(ResultSet rs) throws SQLException;
    }
}
