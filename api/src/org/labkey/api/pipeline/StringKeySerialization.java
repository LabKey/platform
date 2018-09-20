package org.labkey.api.pipeline;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializer;

import java.io.IOException;
import java.net.URI;

// For any class that can serialize via toString() and deserialize with a T(String) constructor
public class StringKeySerialization
{

    public static class Serializer extends StdKeySerializer
    {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            String valueStr = value.toString();
            gen.writeFieldName(valueStr);
        }
    }

    public static class URIDeserializer extends KeyDeserializer
    {
        @Override
        public URI deserializeKey(String key, DeserializationContext ctxt) throws IOException
        {
            return URI.create(key);
        }
    }
}
