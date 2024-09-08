package org.labkey.core.admin;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationProviderFactory;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

public class DisplayFormatValidationProviderFactory implements SiteValidationProviderFactory
{
    @Override
    public String getName()
    {
        return "Display Format Validator";
    }

    @Override
    public String getDescription()
    {
        return "Report non-standard display formats";
    }

    @Override
    public SiteValidationProvider getSiteValidationProvider()
    {
        return new SiteValidationProvider()
        {
            @Override
            public SiteValidationProviderFactory getFactory()
            {
                return DisplayFormatValidationProviderFactory.this;
            }

            @Override
            public @Nullable SiteValidationResultList runValidation(Container c, User u)
            {
                return null;
            }
        };
    }

//    @Override
//    public @Nullable SiteValidationResultList runValidation(Container c, User u)
//    {
//        PropertyManager.getNormalStore().streamMatchingContainers(SITE_CONFIG_USER, LOOK_AND_FEEL_SET_NAME)
//            .forEach(container -> {System.out.println(container);});
//        // TODO: Check folder settings
//        // TODO: Check query XML
//        // TODO: Check property descriptors
//        return null;
//    }
}
