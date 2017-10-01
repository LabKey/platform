package org.labkey.api.compliance;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

public interface TableRulesProvider
{
    /**
     * Returns the TableRules for this Container and User, if applicable. Otherwise returns null.
     *
     * @param c Current container
     * @param user Current user
     * @return The TableRules to apply here, or null if this provider is not interested
     */
    @Nullable TableRules get(Container c, User user);
}
