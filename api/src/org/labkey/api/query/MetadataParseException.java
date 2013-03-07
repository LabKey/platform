package org.labkey.api.query;

import org.json.JSONObject;

/**
 * User: Nick Arnold
 * Date: 3/6/13
 */
public class MetadataParseException extends QueryParseException
{
    public MetadataParseException(String message, Throwable cause, int line, int column)
    {
        super(message, cause, line, column);
    }

    public MetadataParseException(String queryName, QueryParseException other)
    {
        super(queryName + ":" + other.getMessage(), other.getCause(), other._line, other._column);
    }

    @Override
    public JSONObject toJSON(String metadata)
    {
        JSONObject json = super.toJSON(metadata);
        json.put("type", "xml");
        return json;
    }
}
