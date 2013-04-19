package org.labkey.api.etl;


import org.labkey.api.query.SchemaKey;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-04-16
 * Time: 9:19 AM
 *
 * This is a simple POJO that can be used to describe a simple copy operation.
 * Doesn't specify anything about filtering of source, etc.  However, for convienence, does
 * have a place to put name of a timestamp column
 */
public class CopyConfig
{
    protected SchemaKey _sourceSchema;
    protected String _sourceQuery;
    protected String _sourceTimestampColumnName = "modified";
    protected SourceOptions _sourceOptions = null;

    protected SchemaKey _targetSchema;
    protected String _targetQuery;
    protected TargetOptions _targetOptions = TargetOptions.append;

    public CopyConfig()
    {
    }

    public CopyConfig(String sourceSchema, String source, String targetSchema, String target)
    {
        this._sourceSchema = SchemaKey.decode(sourceSchema);
        this._sourceQuery = source;
        this._targetSchema = SchemaKey.decode(targetSchema);
        this._targetQuery = target;
    }

    enum TargetOptions
    {
        merge,
        append,
        truncateAndAppend
    }

    enum SourceOptions
    {
        deleteRowsAfterSelect
    }


    public SchemaKey getSourceSchema()
    {
        return _sourceSchema;
    }

    public void setSourceSchema(SchemaKey sourceSchema)
    {
        this._sourceSchema = sourceSchema;
    }

    public String getSourceQuery()
    {
        return _sourceQuery;
    }

    public void setSourceQuery(String sourceQuery)
    {
        this._sourceQuery = sourceQuery;
    }

    public String getSourceTimestampColumnName()
    {
        return _sourceTimestampColumnName;
    }

    public void setSourceTimestampColumnName(String sourceTimestampColumnName)
    {
        this._sourceTimestampColumnName = sourceTimestampColumnName;
    }

    public SchemaKey getTargetSchema()
    {
        return _targetSchema;
    }

    public void setTargetSchema(SchemaKey targetSchema)
    {
        this._targetSchema = targetSchema;
    }

    public String getTargetQuery()
    {
        return _targetQuery;
    }

    public void setTargetQuery(String targetQuery)
    {
        this._targetQuery = targetQuery;
    }

    public SourceOptions getSourceOptions()
    {
        return _sourceOptions;
    }

    public void setSourceOptions(SourceOptions sourceOptions)
    {
        _sourceOptions = sourceOptions;
    }

    public TargetOptions getTargetOptions()
    {
        return _targetOptions;
    }

    public void setTargetOptions(TargetOptions targetOptions)
    {
        _targetOptions = targetOptions;
    }
}
