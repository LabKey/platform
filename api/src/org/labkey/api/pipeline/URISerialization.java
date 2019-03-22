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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.net.URI;

public class URISerialization
{
    public static class Serializer extends StdSerializer<URI>
    {
        public Serializer()
        {
            this(null);
        }

        public Serializer(Class<URI> typ)
        {
            super(typ);
        }

        @Override
        public void serialize(URI uri, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            String str = null != uri ? PipelineJobService.get().getPathMapper().localToRemote(uri.toString()) : null;
            gen.writeString(str);
        }

        @Override
        public void serializeWithType(URI uri, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException
        {
            WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(uri, URI.class, JsonToken.VALUE_STRING));
            serialize(uri, gen, provider);
            typeSer.writeTypeSuffix(gen, typeIdDef);
        }

    }

    public static class Deserializer extends StdDeserializer<URI>
    {
        protected Deserializer()
        {
            super(URI.class);
        }

        @Override
        public URI deserialize(JsonParser parser, DeserializationContext context) throws IOException
        {
            String str = parser.getValueAsString();
            return null != str ? URI.create(PipelineJobService.get().getPathMapper().remoteToLocal(str)) : null;
        }
    }
}
