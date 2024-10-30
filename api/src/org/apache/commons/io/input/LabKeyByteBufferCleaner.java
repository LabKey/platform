package org.apache.commons.io.input;

import java.nio.ByteBuffer;

// Provides access to org.apache.commons.io.input.ByteBufferCleaner, which is package-private
public class LabKeyByteBufferCleaner
{
    private static final boolean _supported = ByteBufferCleaner.isSupported();

    public static void clean(ByteBuffer buffer)
    {
        if (_supported)
        {
            ByteBufferCleaner.clean(buffer);
        }
    }
}
