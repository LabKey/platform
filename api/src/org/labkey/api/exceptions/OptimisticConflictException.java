/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.exceptions;

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;

import java.sql.SQLException;

/**
 * Thrown to indicate that the database has changed state from the precondition assumption made by this operation.
 * For example, if a row is being deleted and the DELETE statement to the database indicates that no rows were actually
 * removed.
 * Created by adam on 1/17/2017.
 */
public class OptimisticConflictException extends RuntimeSQLException
{
    public OptimisticConflictException(String errorMessage, String sqlState, int error)
    {
        super(new SQLException(errorMessage, sqlState, error));
    }

    @Override
    public boolean isConstraintException()
    {
        return true;
    }

    public static OptimisticConflictException create(int error)
    {
        switch (error)
        {
            case Table.ERROR_DELETED:
                return new OptimisticConflictException("Optimistic concurrency exception: Row deleted",
                        Table.SQLSTATE_TRANSACTION_STATE,
                        error);
            case Table.ERROR_ROWVERSION:
                return new OptimisticConflictException("Optimistic concurrency exception: Row updated",
                        Table.SQLSTATE_TRANSACTION_STATE,
                        error);
            case Table.ERROR_TABLEDELETED:
                return new OptimisticConflictException("Optimistic concurrency exception: Table deleted",
                        Table.SQLSTATE_TRANSACTION_STATE,
                        error);
        }
        assert false : "unexpected error code";
        return null;
    }
}
