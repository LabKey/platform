package org.labkey.api.security.permissions;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class DeleteUserPermission extends AdminPermission
{
    public DeleteUserPermission()
    {
        super("Delete a User", "Allows a role to delete users from the server");
    }

    @Override
    public @NotNull Collection<String> getSerializationAliases()
    {
        // Support legacy name
        return List.of("org.labkey.api.security.permissions.UserManagementPermission");
    }
}