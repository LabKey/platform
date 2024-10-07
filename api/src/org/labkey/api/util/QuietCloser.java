package org.labkey.api.util;

/**
 * Variant of AutoCloseable that doesn't throw any checked exceptions.
 */
public interface QuietCloser extends AutoCloseable
{
    @Override
    void close();
}
