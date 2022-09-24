package org.labkey.api.util;

import com.fasterxml.jackson.databind.ser.std.StdSerializer;

@Deprecated
abstract class JSONBaseSerializer<T> extends StdSerializer<T>
{
    private static final long serialVersionUID = 1L;

    protected JSONBaseSerializer(Class<T> cls) { super(cls); }
}
