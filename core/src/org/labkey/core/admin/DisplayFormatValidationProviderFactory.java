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
            public @Nullable SiteValidationResultList runValidation(Container container, User u)
            {
                SiteValidationResultList results = new SiteValidationResultList();
                _analyzer.handle(container, u, (c, type, format, contextProvider) -> {
                    DisplayFormatAnalyzer.DisplayFormatContext context = contextProvider.get();
                    results.addWarn(context.message() + ": " + format, context.url());
                });

                return results.nullIfEmpty();
            }
        };
    }
}
