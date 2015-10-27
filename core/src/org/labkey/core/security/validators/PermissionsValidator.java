package org.labkey.core.security.validators;

import org.labkey.api.admin.sitevalidation.SiteValidationProviderImpl;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.ReaderRole;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 10/25/2015
 *
 * Validates appropriate container level permissions for the guest user.
 */
public class PermissionsValidator extends SiteValidationProviderImpl
{
    @Override
    public String getName()
    {
        return "Permissions Validator";
    }

    @Override
    public String getDescription()
    {
        return "Check that the guest user permissions are set appropriately.";
    }

    @Override
    public boolean isSiteScope()
    {
        return false;
    }

    @Override
    public SiteValidationResultList runValidation(Container c, User u)
    {
        SiteValidationResultList results = new SiteValidationResultList();

        Set<Class<? extends Permission>> readerPermissions = new ReaderRole().getPermissions();
        if (c.hasOneOf(User.guest, readerPermissions))
        {
            results.addInfo("Guest user has read permission for folder.");
        }
        Set<Class<? extends Permission>> editorPermissions = new HashSet<>(new EditorRole().getPermissions());
        editorPermissions.removeAll(readerPermissions);
        if (c.hasOneOf(User.guest, Collections.unmodifiableSet(editorPermissions)))
        {
            results.addWarn("Guest user has edit permission for folder.");
        }
        return results.nullIfEmpty();
    }
}
