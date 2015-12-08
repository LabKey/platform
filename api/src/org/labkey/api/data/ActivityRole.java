package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.Permission;

/**
 * Represents the set of activity roles that are used to set PHI access
 */
public interface ActivityRole
{
    String getName();

    String getDescription();

    boolean requiresIRB();

    String getDefaultIRB();

    @NotNull
    Class<? extends Permission> getRequiredPermission();
}