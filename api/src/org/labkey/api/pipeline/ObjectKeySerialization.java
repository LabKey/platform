/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
