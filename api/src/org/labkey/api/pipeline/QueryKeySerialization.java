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
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.SchemaKey;

import java.io.IOException;

public class QueryKeySerialization
{
    public static class Serializer extends StdSerializer<QueryKey>
    {
        public Serializer()
        {
            this(null);
        }

        public Serializer(Class<QueryKey> typ)
        {
            super(typ);
        }

        @Override
        public void serialize(QueryKey queryKey, JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            gen.writeFieldName("_parent");
            gen.getCodec().writeValue(gen, queryKey.getParent());
            gen.writeFieldName("_name");
            gen.getCodec().writeValue(gen, queryKey.getName());
        }

        @Override
        public void serializeWithType(QueryKey queryKey, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException
        {
            WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(queryKey, JsonToken.START_OBJECT));
            serialize(queryKey, gen, provider);
            typeSer.writeTypeSuffix(gen, typeIdDef);
        }
    }

    public static class SchemaKeyDeserializer extends StdDeserializer<SchemaKey>
    {
        protected SchemaKeyDeserializer()
        {
            super(SchemaKey.class);
        }

        @Override
        public SchemaKey deserialize(JsonParser parser, DeserializationContext context) throws IOException
        {
            if (parser.isExpectedStartObjectToken())
            {
                SchemaKey parent = null;
                String name = null;
                while (parser.nextToken() != JsonToken.END_OBJECT)
                {
                    String fieldName = parser.getCurrentName();
                    JsonToken token1 = parser.nextToken();       // get past FIELD_NAME
                    if ("_parent".equals(fieldName))
                        parent = parser.readValueAs(SchemaKey.class);
                    else if ("_name".equals(fieldName))
                        name = parser.getValueAsString();
                }
                if (null == name)
                    throw new IOException("Expected SchemaKey to contain non-null name.");

                return new SchemaKey(parent, name);
            }
            else if (JsonToken.VALUE_NULL.equals(parser.getCurrentToken()))
            {
                return null;
            }
            else
            {
                throw new IOException("Unexpected token in serialized SchemaKey: " + parser.getCurrentToken());
            }
        }

        @Override
        public SchemaKey deserializeWithType(JsonParser parser, DeserializationContext context, TypeDeserializer typeDeserializer) throws IOException
        {
            return (SchemaKey) typeDeserializer.deserializeTypedFromArray(parser, context);
        }
    }

    public static class FieldKeyDeserializer extends StdDeserializer<FieldKey>
    {
        protected FieldKeyDeserializer()
        {
            super(FieldKey.class);
        }

        @Override
        public FieldKey deserialize(JsonParser parser, DeserializationContext context) throws IOException
        {
            if (parser.isExpectedStartObjectToken())
            {
                FieldKey parent = null;
                String name = null;
                while (parser.nextToken() != JsonToken.END_OBJECT)
                {
                    String fieldName = parser.getCurrentName();
                    JsonToken token1 = parser.nextToken();       // get past FIELD_NAME
                    if ("_parent".equals(fieldName))
                        parent = parser.readValueAs(FieldKey.class);
                    else if ("_name".equals(fieldName))
                        name = parser.getValueAsString();
                }
                if (null == name)
                    throw new IOException("Expected FieldKey to contain non-null name.");

                return new FieldKey(parent, name);
            }
            else if (JsonToken.VALUE_NULL.equals(parser.getCurrentToken()))
            {
                return null;
            }
            else
            {
                throw new IOException("Unexpected token in serialized FieldKey: " + parser.getCurrentToken());
            }
        }

        @Override
        public FieldKey deserializeWithType(JsonParser parser, DeserializationContext context, TypeDeserializer typeDeserializer) throws IOException
        {
            return (FieldKey) typeDeserializer.deserializeTypedFromArray(parser, context);
        }
    }
}
