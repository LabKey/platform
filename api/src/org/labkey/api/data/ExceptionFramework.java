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

import org.springframework.dao.DataAccessException;
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
            DataAccessException translate(DbScope scope, String task, SQLException e)
            {
                SQLExceptionTranslator translator = new SQLErrorCodeSQLExceptionTranslator(scope.getDataSource());
                return translator.translate(task, null, e);
            }
        },
    JDBC
        {
            @Override
            RuntimeSQLException translate(DbScope scope, String task, SQLException e)
            {
                return new RuntimeSQLException(e);
            }
        };

    abstract RuntimeException translate(DbScope scope, String task, SQLException e);
}
