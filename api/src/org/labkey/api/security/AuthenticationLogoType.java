package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;

import java.util.Collection;
import java.util.LinkedList;

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
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        Collection<String> validLogoNames = new LinkedList<>();

        for (SSOAuthenticationProvider provider : AuthenticationProviderCache.getProviders(SSOAuthenticationProvider.class))
        {
            validLogoNames.add(AuthenticationManager.HEADER_LOGO_PREFIX + provider.getName());
            validLogoNames.add(AuthenticationManager.LOGIN_PAGE_LOGO_PREFIX + provider.getName());
        }

        if (!validLogoNames.isEmpty())
        {
            sql.append(parentColumn).append(" = '").append(ContainerManager.getRoot().getId());
            sql.append("' AND ").append(documentNameColumn).append(" IN (");
            sql.append(StringUtils.repeat("?", ", ", validLogoNames.size()));
            sql.addAll(validLogoNames);
            sql.append(")");
        }
    }
}
