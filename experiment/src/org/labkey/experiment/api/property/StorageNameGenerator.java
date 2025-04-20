package org.labkey.experiment.api.property;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;

import java.util.Set;

/**
 * Truncates names based on dialect-specific rules and guarantees case-insensitive uniqueness among all claimed and
 * generated names. Similar to AliasManager except it doesn't attempt to "sanitize" any characters. Callers are
 * responsible for quoting these names if necessary.
 */
public class StorageNameGenerator
{
    // Leave room for MV suffix in case the column is changed to MV later and leave room for uniquifying suffix
    private static final int RESERVED_LENGTH = OntologyManager.MV_INDICATOR_SUFFIX.length() + 1 + 3;

    private final SqlDialect _dialect;
    private final Set<String> _names = new CaseInsensitiveHashSet();

    public StorageNameGenerator(@NotNull SqlDialect dialect)
    {
        _dialect = dialect;
    }

    public String claimName(String name)
    {
        if (!_names.add(name))
            throw new IllegalStateException("This name was already claimed! " + name);

        return name;
    }

    /**
     * Generate a storage name based on the provided candidate name. If needed, the name is truncated based on
     * dialect-specific rules and uniquified with an incrementing suffix (1, 2, 3, ...).
     */
    public String generateName(String candidateName)
    {
        String legalName = _dialect.truncate(candidateName, RESERVED_LENGTH);
        // TODO: expand StorageColumnName column to 255 and remove this check
        if (legalName.length() > 97)
            legalName = legalName.substring(0, 97);

        String ret = legalName;

        for (int i = 1; _names.contains(ret); i++)
        {
            ret = legalName + i;
        }

        return claimName(ret);
    }
}
