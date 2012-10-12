/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 10/25/11
 * Time: 11:35 PM
 */
public abstract class JdbcCommand
{
    private static final Logger LOG = Logger.getLogger(JdbcCommand.class);

    private final @NotNull DbScope _scope;
    private final @Nullable Connection _conn;
    private ExceptionFramework _exceptionFramework = ExceptionFramework.Spring;
    private Level _logLevel = Level.WARN;  // Log all warnings and errors by default
    private Logger _log = LOG;   // Passed to getConnection(), can be customized via getLogger()
    private @Nullable AsyncQueryRequest _asyncRequest = null;
    private @Nullable StackTraceElement[] _loggingStacktrace = null;

    protected JdbcCommand(@NotNull DbScope scope, @Nullable Connection conn)
    {
        _scope = scope;
        _conn = conn;
    }

    public Connection getConnection() throws SQLException
    {
        return null == _conn ? _scope.getConnection(_log) : _conn;
    }

    protected void close(@Nullable ResultSet rs, Connection conn)
    {
        // Close Connection only if we got it from the scope (i.e., _conn is null)
        @Nullable Connection connToClose = (null == _conn ? conn : null);
        Table.doFinally(rs, null, connToClose, getScope());
    }

    @NotNull
    public DbScope getScope()
    {
        return _scope;
    }

    public void setExceptionFramework(ExceptionFramework exceptionFramework)   // TODO: Chaining?
    {
        _exceptionFramework = exceptionFramework;
    }

    public ExceptionFramework getExceptionFramework()
    {
        return _exceptionFramework;
    }

    public Level getLogLevel()
    {
        return _logLevel;
    }

    public void setLogLevel(Level logLevel)    // TODO: Chaining?
    {
        _logLevel = logLevel;
    }

    public void setLogger(@NotNull Logger log)    // TODO: Chaining?
    {
        _log = log;
    }

    public void setAsyncRequest(@Nullable AsyncQueryRequest asyncRequest)
    {
        _asyncRequest = asyncRequest;

        if (null != asyncRequest)
            _loggingStacktrace = asyncRequest.getCreationStackTrace();
    }

    @Nullable
    protected AsyncQueryRequest getAsyncRequest()
    {
        return _asyncRequest;
    }

    public void setLoggingStacktrace(@Nullable StackTraceElement[] loggingStacktrace)
    {
        _loggingStacktrace = loggingStacktrace;
    }

    @Nullable
    protected StackTraceElement[] getLoggingStacktrace()
    {
        return _loggingStacktrace;
    }
}
