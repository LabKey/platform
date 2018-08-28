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
        gen.writeFieldName("first");
        gen.getCodec().writeValue(gen, pair.first);
        gen.writeFieldName("second");
        gen.getCodec().writeValue(gen, pair.second);
    }

    @Override
    public void serializeWithType(Pair<Type1, Type2> map, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException
    {
        WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(map, JsonToken.START_OBJECT));
        serialize(map, gen, provider);
        typeSer.writeTypeSuffix(gen, typeIdDef);
    }
}
