package org.labkey.api.data;

public enum LookupResolutionType
{
    primaryKey(true, false, true), // known that the use will always supply the pk value
    alternateKey(false, true, false), // known that the use will never supply the pk value
    alternateThenPrimaryKey(true, true, false), // If there is a situation where it's sometimes a primary and sometimes an alternate key, check for the alternate key first
    // TODO do we need to support this? If not, we can change the properties here to just have a "supportsAlternateKey" since alternate will always be checked first.
    primaryThenAlternateKey(true, true, true); // this most closely matches previous behavior when allowImportLookupByAlternateKey was true (added for compatibility; prefer the other options)

    final boolean _useAlternateKey;
    final boolean _usePrimaryKey;
    final boolean _usePrimaryFirst;

    LookupResolutionType(boolean usePrimaryKey, boolean useAlternateKey, boolean usePrimaryFirst)
    {
        _usePrimaryKey = usePrimaryKey;
        _useAlternateKey = useAlternateKey;
        _usePrimaryFirst = usePrimaryFirst;
    }

    public boolean useAlternateKey()
    {
        return _useAlternateKey;
    }

    public boolean usePrimaryKey()
    {
        return _usePrimaryKey;
    }

    public boolean usePrimaryFirst()
    {
        return _usePrimaryFirst;
    }
}
