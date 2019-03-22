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
