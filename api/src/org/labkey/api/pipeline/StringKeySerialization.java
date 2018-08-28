package org.labkey.api.pipeline;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializer;

import java.io.IOException;

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

    public static class Deserializer<T> extends KeyDeserializer
    {
        @Override
        public T deserializeKey(String key, DeserializationContext ctxt) throws IOException
        {
            ObjectMapper mapper = new ObjectMapper();
            try
            {
                TypeReference<T> typeRef = new TypeReference<T>() {};
                T result = mapper.convertValue(key, typeRef);
                return result;
            }
            catch (Exception e)
            {
                throw new IOException(e);
            }
        }
    }
}
