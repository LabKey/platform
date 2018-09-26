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
