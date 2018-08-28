package org.labkey.api.pipeline;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.util.Pair;

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
