package org.labkey.di.filters;

import java.io.Serializable;

/**
 * User: tgaluhn
 * Date: 4/21/2015
 */
public class DeletedRowsSource implements Serializable
{
    private String _schemaName;
    private String _queryName;
    private String _timestampColumnName;
    private String _runColumnName;
    private String _deletedSourceKeyColumnName;
    private String _targetKeyColumnName;

    public DeletedRowsSource(String schemaName, String queryName, String timestampColumnName, String runColumnName, String deletedSourceKeyColumnName, String targetKeyColumnName)
    {
        _schemaName = schemaName;
        _queryName = queryName;
        _timestampColumnName = timestampColumnName;
        _runColumnName = runColumnName;
        _deletedSourceKeyColumnName = deletedSourceKeyColumnName;
        _targetKeyColumnName = targetKeyColumnName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public String getTimestampColumnName()
    {
        return _timestampColumnName;
    }

    public String getRunColumnName()
    {
        return _runColumnName;
    }

    public String getDeletedSourceKeyColumnName()
    {
        return _deletedSourceKeyColumnName;
    }

    public String getTargetKeyColumnName()
    {
        return _targetKeyColumnName;
    }
}
