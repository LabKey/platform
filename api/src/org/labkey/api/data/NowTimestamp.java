package org.labkey.api.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.sql.Timestamp;

/**
 * Marker interface to hint that this value may be replaced by CURRENT_TIMESTAMP.
 */
@JsonDeserialize(using = NowTimestamp.NowTimestampDeserializer.class)
public class NowTimestamp extends java.sql.Timestamp
{
    public NowTimestamp()
    {
        this(System.currentTimeMillis());
    }

    public NowTimestamp(long ms)
    {
        super(ms);
    }

    public static class NowTimestampDeserializer extends JsonDeserializer<NowTimestamp>
    {
        @Override
        public NowTimestamp deserialize(JsonParser p, DeserializationContext ctx) throws IOException
        {
            JsonToken token = p.getCurrentToken();

            if (token == JsonToken.VALUE_STRING)
            {
                String text = p.getText().trim();

                try
                {
                    Timestamp ts = Timestamp.valueOf(text);
                    return new NowTimestamp(ts.getTime());
                }
                catch (IllegalArgumentException ex)
                {
                    throw ctx.weirdStringException(text, NowTimestamp.class, "Expected a valid timestamp string.");
                }
            }

            if (token == JsonToken.VALUE_NUMBER_INT)
            {
                long milliseconds = p.getLongValue();
                return new NowTimestamp(milliseconds);
            }

            throw ctx.wrongTokenException(p, NowTimestamp.class, JsonToken.VALUE_STRING, "Expected timestamp as String or long.");
        }
    }
}
