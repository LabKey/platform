package org.labkey.api.pipeline;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;
import java.sql.Time;

public class SqlTimeSerialization
{
    public static class SqlTimeSerializer extends com.fasterxml.jackson.databind.ser.std.SqlTimeSerializer
    {
        @Override
        public void serialize(java.sql.Time value, JsonGenerator g, SerializerProvider provider) throws IOException
        {
            g.writeNumber(value.getTime());
        }
    }

    public static class SqlTimeDeserializer extends StdScalarDeserializer<Time>
    {
        protected SqlTimeDeserializer()
        {
            super(Time.class);
        }

        @Override
        public java.sql.Time deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
        {
            return new Time(p.getLongValue());
        }
    }
}
