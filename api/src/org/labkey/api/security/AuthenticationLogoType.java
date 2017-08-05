package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.ContainerManager;

public class AuthenticationLogoType implements AttachmentType
{
    private static final AuthenticationLogoType INSTANCE = new AuthenticationLogoType();

    public static AuthenticationLogoType get()
    {
        return INSTANCE;
    }

    private AuthenticationLogoType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public @Nullable String getSelectSqlForIds()
    {
        return "SELECT CAST('" + ContainerManager.getRoot().getId() + "' AS VARCHAR(500)) AS ID";  // TODO: need to filter based on document names!
    }
}
