package org.labkey.api.exceptions;

/**
 * An IllegalStateException that provides a bit more context. Callers that are particularly susceptible to table and
 * container delete race conditions (e.g., background tasks) can catch this specific exception, then suppress or retry.
 *
 * Created by adam on 1/17/2017.
 */
public class TableNotFoundException extends IllegalStateException
{
    private final String _schemaName;
    private final String _tableName;

    public TableNotFoundException(String schemaName, String tableName)
    {
        super("Table not found (deleted? race condition?)");

        _schemaName = schemaName;
        _tableName = tableName;
    }

    public String getFullName()
    {
        return getSchemaName() + "." + getTableName();
    }

    @Override
    public String getMessage()
    {
        return super.getMessage() + ": " + getFullName();
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getTableName()
    {
        return _tableName;
    }
}
