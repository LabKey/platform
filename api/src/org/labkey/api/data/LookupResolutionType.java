package org.labkey.api.data;

public enum LookupResolutionType
{
    primaryKey(false), // known that the user will always supply the pk value
    alternateThenPrimaryKey(true); // If there is a situation where it's sometimes a primary and sometimes an alternate key, check for the alternate key first

    final boolean _useAlternateKey;

    LookupResolutionType(boolean useAlternateKey)
    {
        _useAlternateKey = useAlternateKey;
    }

    public boolean useAlternateKey()
    {
        return _useAlternateKey;
    }

}
