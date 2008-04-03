package org.labkey.api.query;

public class MetadataException extends QueryException
{
    public MetadataException(String message)
    {
        super(message);
    }

    public MetadataException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
