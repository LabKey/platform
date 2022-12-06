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

import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.DebugInfoDumper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import java.sql.SQLException;

/**
* User: adam
* Date: 10/25/11
* Time: 11:26 PM
*/
public enum ExceptionFramework
{
    Spring
        {
            @Override
            public RuntimeException translate(DbScope scope, String task, SQLException e)
            {
                SQLExceptionTranslator translator = new SQLErrorCodeSQLExceptionTranslator(scope.getDataSource());
                if (SqlDialect.isTransactionException(e))
                {
                    DebugInfoDumper.dumpThreads(1);
                }
                RuntimeException result = translator.translate(task, null, e);
                if (result == null)
                {
                    result = new UncategorizedSQLException(task, null, e);
                }
                return result;
            }
        },
    JDBC
        {
            @Override
            public RuntimeSQLException translate(DbScope scope, String task, SQLException e)
            {
                return new RuntimeSQLException(e);
            }
        };

    public abstract RuntimeException translate(DbScope scope, String task, SQLException e);

    public RuntimeException translate(DbScope scope, String task, RuntimeSQLException e)
    {
        return translate(scope, task, e.getSQLException());
    }
}
