package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;

/**
 * Use this class when you want your custom exception message to be displayed instead of the standard one we
 * construct using the value and target type for the conversion.
 */
public class ConversionExceptionWithMessage extends ConversionException
{
    public ConversionExceptionWithMessage(String message)
    {
        super(message);
    }
}