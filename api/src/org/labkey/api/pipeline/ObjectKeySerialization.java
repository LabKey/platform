package org.labkey.api.pipeline;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializer;

import java.io.IOException;

public class ObjectKeySerialization
{
    public static class Serializer extends StdKeySerializer
    {
        private ObjectMapper mapper = PipelineJob.createObjectMapper();

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            String valueStr = mapper.writeValueAsString(value);
            gen.writeFieldName(valueStr);
        }
    }

    public static class Deserializer<T> extends KeyDeserializer
    {

        @Override
        public T deserializeKey(String key, DeserializationContext ctxt) throws IOException
        {
            ObjectMapper mapper = PipelineJob.createObjectMapper();
            try
            {
                TypeReference<T> typeRef = new TypeReference<T>() {};
                T prop = mapper.readValue(key, typeRef);
                return prop;
            }
            catch (Exception e)
            {
                throw new IOException(e);
            }
        }
    }
}
