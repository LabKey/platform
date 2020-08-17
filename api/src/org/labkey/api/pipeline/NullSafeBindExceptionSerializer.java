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
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.labkey.api.action.NullSafeBindException;

import java.io.IOException;

public class NullSafeBindExceptionSerializer extends StdSerializer<NullSafeBindException>
{
    public NullSafeBindExceptionSerializer()
    {
        this(null);
    }

    public NullSafeBindExceptionSerializer(Class<NullSafeBindException> typ)
    {
        super(typ);
    }

    @Override
    public void serialize(NullSafeBindException nsb, JsonGenerator gen, SerializerProvider provider) throws IOException
    {
        gen.writeFieldName("target");
        gen.getCodec().writeValue(gen, nsb.getTarget());
        gen.writeFieldName("objectName");
        gen.getCodec().writeValue(gen, nsb.getObjectName());
    }

    @Override
    public void serializeWithType(NullSafeBindException nsb, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException
    {
        WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(nsb, JsonToken.START_OBJECT));
        serialize(nsb, gen, provider);
        typeSer.writeTypeSuffix(gen, typeIdDef);
    }
}
