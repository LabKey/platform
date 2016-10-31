package org.labkey.api.admin.sitevalidation;

import org.jetbrains.annotations.NotNull;

/**
 * User: tgaluhn
 * Date: 10/30/2016
 */
public interface SiteValidatorDescriptor extends Comparable<SiteValidatorDescriptor>
{
    String getName();
    String getDescription();

    @Override
    default int compareTo(@NotNull SiteValidatorDescriptor d)
    {
        return getName().compareToIgnoreCase(d.getName());
    }
}
