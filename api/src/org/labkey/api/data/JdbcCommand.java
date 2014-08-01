/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
public abstract class JdbcCommand<COMMAND extends JdbcCommand>
{
    private static final Logger LOG = Logger.getLogger(JdbcCommand.class);

    private final @NotNull DbScope _scope;
    private final @Nullable Connection _conn;

    private ExceptionFramework _exceptionFramework = ExceptionFramework.Spring;
    private Level _logLevel = Level.WARN;  // Log all warnings and errors by default
    private Logger _log = LOG;   // Passed to getConnection(), can be customized via setLogger()

    protected JdbcCommand(@NotNull DbScope scope, @Nullable Connection conn)
    {
        _scope = scope;
        _conn = conn;
    }

    // COMMAND and getThis() make it easier to chain setLogLevel(), setLogger(), setExceptionFramework(), and subclass methods
    // while returning the correct selector type from subclasses
    abstract protected COMMAND getThis();

    public Connection getConnection() throws SQLException
    {
        return null == _conn ? _scope.getConnection(_log) : _conn;
    }

    protected void close(@Nullable ResultSet rs, @Nullable Connection conn)
    {
        // Close Connection only if we got it from the scope (i.e., _conn is null)
        @Nullable Connection connToClose = (null == _conn ? conn : null);
        Table.doClose(rs, null, connToClose, getScope());
    }

    protected void afterComplete(ResultSet rs)
    {
    }

    @NotNull
    public DbScope getScope()
    {
        return _scope;
    }

    public COMMAND setExceptionFramework(ExceptionFramework exceptionFramework)
    {
        _exceptionFramework = exceptionFramework;
        return getThis();
    }

    public ExceptionFramework getExceptionFramework()
    {
        return _exceptionFramework;
    }

    public Level getLogLevel()
    {
        return _logLevel;
    }

    public COMMAND setLogLevel(Level logLevel)
    {
        _logLevel = logLevel;
        return getThis();
    }

    public COMMAND setLogger(@NotNull Logger log)
    {
        _log = log;
        return getThis();
    }
}
