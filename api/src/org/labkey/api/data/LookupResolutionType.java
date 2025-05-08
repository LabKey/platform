package org.labkey.api.data;

public enum LookupResolutionType
{
    primaryKey(false, true), // known that the user will always supply the pk value
    alternateThenPrimaryKey(true, false), // If there is a situation where it's sometimes a primary and sometimes an alternate key, check for the alternate key first
    primaryThenAlternateKey(true, true); // this most closely matches previous behavior when allowImportLookupByAlternateKey was true (used for internal purposes)

    final boolean _useAlternateKey;
    final boolean _usePrimaryFirst;

    LookupResolutionType(boolean useAlternateKey, boolean usePrimaryFirst)
    {
        _useAlternateKey = useAlternateKey;
        _usePrimaryFirst = usePrimaryFirst;
    }

    public boolean useAlternateKey()
    {
        return _useAlternateKey;
    }

    public boolean usePrimaryFirst()
    {
        return _usePrimaryFirst;
    }
}
