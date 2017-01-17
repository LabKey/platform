package org.labkey.api.exceptions;

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;

import java.sql.SQLException;

/**
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
