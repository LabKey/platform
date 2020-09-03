package org.labkey.api.security.permissions;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class UpdateUserPermission extends AdminPermission
{
    public UpdateUserPermission()
    {
        super("Update a User", "Allows a role to update users");
    }

    @Override
    public @NotNull Collection<String> getSerializationAliases()
    {
        // Support legacy name
        return List.of("org.labkey.api.security.permissions.UserManagementPermission");
    }
}