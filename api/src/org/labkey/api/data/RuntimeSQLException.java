/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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

import com.google.gwt.user.client.rpc.IsSerializable;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.dialect.SqlDialect;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * Wrapper to convert the checked {@link SQLException} to a {@link RuntimeException}, retaining much of the special
 * SQLException behavior, such as chaining causes.
 * User: mbellew
 * Date: Mar 23, 2005
 */
public class RuntimeSQLException extends RuntimeException implements Serializable, IsSerializable
{
    // don't want to use cause, I want to impersonate the cause
    SQLException sqlx;

    public RuntimeSQLException(SQLException x)
    {
        sqlx = x;
    }

    @Override
    public String getMessage()
    {
        return sqlx.getMessage();
    }

    @Override
    public String getLocalizedMessage()
    {
        return sqlx.getLocalizedMessage();
    }

    @Override
    public Throwable getCause()
    {
        return sqlx.getCause();
    }

    public String getSQLState()
    {
        return sqlx.getSQLState();
    }

    @Override
    public synchronized Throwable initCause(Throwable cause)
    {
        return sqlx.initCause(cause);
    }

    public String toString()
    {
        return sqlx.toString();
    }

    @Override
    public void printStackTrace()
    {
        sqlx.printStackTrace();
    }

    @Override
    public void printStackTrace(PrintStream s)
    {
        sqlx.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintWriter s)
    {
        sqlx.printStackTrace(s);
    }

    @Override
    public synchronized Throwable fillInStackTrace()
    {
        return super.fillInStackTrace();
    }

    @Override
    public StackTraceElement[] getStackTrace()
    {
        return sqlx.getStackTrace();
    }

    @Override
    public void setStackTrace(StackTraceElement[] stackTrace)
    {
        sqlx.setStackTrace(stackTrace);
    }

    public SQLException getSQLException()
    {
        return sqlx;
    }

    public boolean isConstraintException()
    {
        return isConstraintException(getSQLException());
    }

    public static boolean isConstraintException(@NotNull SQLException x)
    {
        String sqlState = x.getSQLState();
        return null != sqlState && (sqlState.equals("23000") || sqlState.equals("23505") || sqlState.equals("23503") ||
         /* TODO: Remove this... OptimisticConflictException gets created with SQLState 25000, which seems wrong (should be 23000?) */ sqlState.equals("25000")) ||
                // Detect errors thrown by trigger used in large column unique constraint
                x.getMessage().startsWith(SqlDialect.CUSTOM_UNIQUE_ERROR_MESSAGE);
    }

    public boolean isNullValueException() { return isNullValueException(getSQLException()); }

    public static boolean isNullValueException(SQLException x)
    {
        String sqlState = x.getSQLState();
        if (null == sqlState || !sqlState.startsWith("23"))
            return false;

        return sqlState.equals("23502");
    }
}
