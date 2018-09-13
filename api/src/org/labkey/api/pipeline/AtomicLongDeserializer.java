package org.labkey.api.pipeline;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicLongDeserializer extends StdScalarDeserializer<AtomicLong>
{
    protected AtomicLongDeserializer()
    {
        super(AtomicLong.class);
    }

    @Override
    public AtomicLong deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        return new AtomicLong(p.getLongValue());
    }

}
