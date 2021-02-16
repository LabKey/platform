package org.labkey.api.pipeline;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.labkey.api.query.SchemaKey;
import org.labkey.remoteapi.query.Filter;

import java.io.IOException;

public class FilterDeserializer extends StdDeserializer<Filter>
{
    protected FilterDeserializer()
    {
        super(Filter.class);
    }

    @Override
    public Filter deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException, JsonProcessingException
    {
        if (parser.isExpectedStartObjectToken())
        {
            String columnName = null;
            Filter.Operator operator = Filter.Operator.EQUAL;
            Object value = null;

            while (parser.nextToken() != JsonToken.END_OBJECT)
            {
                String fieldName = parser.getCurrentName();
                parser.nextToken();       // get past FIELD_NAME
                if ("_columnName".equals(fieldName))
                    columnName = parser.getValueAsString();
                else if ("_operator".equals(fieldName))
                    operator = parser.readValueAs(Filter.Operator.class);
                else if ("_value".equals(fieldName))
                    value = parser.readValueAs(Object.class);
            }
            if (null == columnName)
                throw new IOException("Expected Filter to contain non-null columnName.");

            return new Filter(columnName, value, operator);
        }
        else if (JsonToken.VALUE_NULL.equals(parser.getCurrentToken()))
        {
            return null;
        }
        else
        {
            throw new IOException("Unexpected token in serialized Filter: " + parser.getCurrentToken());
        }
    }
}
