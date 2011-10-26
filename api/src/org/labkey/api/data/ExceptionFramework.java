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
            DataAccessException translate(DbScope scope, String message, String sql, SQLException e)
            {
                SQLExceptionTranslator translator = new SQLErrorCodeSQLExceptionTranslator(scope.getDataSource());
                return translator.translate(message, sql, e);
            }
        },
    JDBC
        {
            @Override
            RuntimeSQLException translate(DbScope scope, String message, String SQL, SQLException e)
            {
                return new RuntimeSQLException(e);
            }
        };

    abstract RuntimeException translate(DbScope scope, String message, String SQL, SQLException e);
}
