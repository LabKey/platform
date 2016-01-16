package org.apache.commons.validator.routines;

/**
 * Adds non-standard TLDs to allowable values for Apache Commons Validator. See issue 25041.
 * Needed because {@see DomainValidator.updateTLDOverride} is public, but its ArrayType argument is package-protected.
 * Created by: jeckels
 * Date: 1/16/16
 */
public class CustomTLDEnabler
{
    static
    {
        DomainValidator.updateTLDOverride(DomainValidator.ArrayType.GENERIC_PLUS, new String[]{"local"});
    }

    public static void initialize()
    {
        // No-op, just used to get static initializer to run
    }
}
