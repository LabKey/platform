package org.labkey.di.steps;

import org.labkey.api.query.SchemaKey;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-04-03
 * Time: 2:22 PM
 *
 * Metadata for a simple query transform
 */
public class SimpleQueryTransformStepMeta
{
    private SchemaKey _sourceSchema;
    private String _sourceQuery;
    private String _timestampColumn = "modifed";
    private SchemaKey _destinationSchema;
    private String _destinationQuery;

    public SchemaKey getSourceSchema()
    {
        return _sourceSchema;
    }

    public void setSourceSchema(SchemaKey sourceSchema)
    {
        _sourceSchema = sourceSchema;
    }

    public String getSourceQuery()
    {
        return _sourceQuery;
    }

    public void setSourceQuery(String sourceQuery)
    {
        _sourceQuery = sourceQuery;
    }

    public String getTimestampColumnName()
    {
        return _timestampColumn;
    }

    public void setTimestampColumnName(String timestampColumn)
    {
        _timestampColumn = timestampColumn;
    }

    public SchemaKey getDestinationSchema()
    {
        return _destinationSchema;
    }

    public void setDestinationSchema(SchemaKey destinationSchema)
    {
        _destinationSchema = destinationSchema;
    }

    public String getDestinationQuery()
    {
        return _destinationQuery;
    }

    public void setDestinationQuery(String destinationQuery)
    {
        _destinationQuery = destinationQuery;
    }
}
