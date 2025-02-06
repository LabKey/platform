package org.labkey.api.util.date;

import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.NotNull;

/**
 * The DateScanner may throw ConversionException, however, the caller
 * should not rely on any particular validation or that the calendar parts values "make sense".
 * All implementations should be thread-safe.
 */
public interface DateScanner
{
    @NotNull
    CalendarParts scan(String datetime) throws ConversionException;
}
