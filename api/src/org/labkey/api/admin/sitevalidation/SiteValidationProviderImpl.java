package org.labkey.api.admin.sitevalidation;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public abstract class SiteValidationProviderImpl implements SiteValidationProvider
{
    @Override
    public boolean shouldRun(Container c, User u)
    {
        return true;
    }

    @Override
    public boolean isSiteScope()
    {
        return false;
    }

    public static SiteValidationResult info(String message)
    {
        return new SiteValidationResult(SiteValidationResult.Level.INFO, message);
    }

    public static SiteValidationResult warn(String message)
    {
        return new SiteValidationResult(SiteValidationResult.Level.WARN, message);
    }

    public static SiteValidationResult error(String message)
    {
        return new SiteValidationResult(SiteValidationResult.Level.ERROR, message);
    }
}
