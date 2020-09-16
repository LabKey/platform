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
import org.labkey.api.util.Pair;

import java.io.IOException;

public class PairSerializer<Type1, Type2> extends StdSerializer<Pair<Type1, Type2>>
{
    public PairSerializer()
    {
        this(null);
    }

    public PairSerializer(Class<Pair<Type1, Type2>> typ)
    {
        super(typ);
    }
    
    @Override
    public void serialize(Pair<Type1, Type2> pair, JsonGenerator gen, SerializerProvider provider) throws IOException
    {
        gen.writeStartObject();
        _serialize(pair, gen, provider);
        gen.writeEndObject();
    }


    public void _serialize(Pair<Type1, Type2> pair, JsonGenerator gen, SerializerProvider provider) throws IOException
    {
        gen.writeFieldName("first");
        gen.getCodec().writeValue(gen, pair.first);
        gen.writeFieldName("second");
        gen.getCodec().writeValue(gen, pair.second);
    }

    @Override
    public void serializeWithType(Pair<Type1, Type2> map, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException
    {
        WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(map, JsonToken.START_OBJECT));
        _serialize(map, gen, provider);
        typeSer.writeTypeSuffix(gen, typeIdDef);
    }
}
