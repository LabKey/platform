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
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.quartz.CronExpression;

import java.io.IOException;
import java.text.ParseException;

/**
 * Custom de/serializer for org.quartz.CronExpression
 * Note only the cronExpression property is serialized; the timezone property is ignored
 *
 * User: tgaluhn
 * Date: 9/20/2018
 */
public class CronExpressionSerialization
{
     public static class Serializer extends StdSerializer<CronExpression>
     {
         public Serializer()
         {
             super(CronExpression.class);
         }

         @Override
         public void serialize(CronExpression value, JsonGenerator gen, SerializerProvider provider) throws IOException
         {
             String expressionString = value == null ? null : value.getCronExpression();
             gen.writeString(expressionString);
         }

         @Override
         public void serializeWithType(CronExpression value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException
         {
             WritableTypeId typeId = typeSer.writeTypePrefix(gen, typeSer.typeId(value, JsonToken.VALUE_STRING));
             serialize(value, gen, serializers);
             typeSer.writeTypeSuffix(gen, typeId);
         }
     }

     public static class Deserializer extends StdScalarDeserializer<CronExpression>
     {
         public Deserializer()
         {
             super(CronExpression.class);
         }

         @Override
         public CronExpression deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
         {
             String expressionString = p.getValueAsString();
             try
             {
                 return expressionString == null ? null : new CronExpression(expressionString);
             }
             catch (ParseException e)
             {
                 throw new JsonParseException(p, "Can't parse CronExpression '" + expressionString + "'", e);
             }
         }
     }
}
