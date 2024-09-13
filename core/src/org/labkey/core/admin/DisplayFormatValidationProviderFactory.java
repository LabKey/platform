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
        return "Report non-standard date and time display formats";
    }

    @Override
    public SiteValidationProvider getSiteValidationProvider()
    {
        return new SiteValidationProvider()
        {
            private final DisplayFormatAnalyzer _analyzer = new DisplayFormatAnalyzer();

            @Override
            public SiteValidationProviderFactory getFactory()
            {
                return DisplayFormatValidationProviderFactory.this;
            }

            @Override
            public @Nullable SiteValidationResultList runValidation(Container c, User u)
            {
                SiteValidationResultList results = new SiteValidationResultList();
                _analyzer.handle(c, u, (c1, type, format, contextProvider) -> results.addWarn(contextProvider.get() + ": " + format));

                return results.nullIfEmpty();
            }
        };
    }
}
