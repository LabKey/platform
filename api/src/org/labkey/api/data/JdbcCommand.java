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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 10/25/11
 * Time: 11:35 PM
 */
public abstract class JdbcCommand
{
    private final DbScope _scope;
    private ExceptionFramework _exceptionFramework = ExceptionFramework.Spring;

    protected JdbcCommand(DbScope scope)
    {
        _scope = scope;
    }

    public Connection getConnection() throws SQLException
    {
        return _scope.getConnection();
    }

    public DbScope getScope()
    {
        return _scope;
    }

    public void setExceptionFramework(ExceptionFramework exceptionFramework)
    {
        _exceptionFramework = exceptionFramework;
    }

    public ExceptionFramework getExceptionFramework()
    {
        return _exceptionFramework;
    }
}
