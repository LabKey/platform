package org.labkey.core.admin;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationProviderFactory;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;

import java.util.Collection;
import java.util.stream.Stream;

import static org.labkey.api.settings.AbstractSettingsGroup.SITE_CONFIG_USER;
import static org.labkey.api.settings.LookAndFeelFolderProperties.LOOK_AND_FEEL_SET_NAME;

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
            private final MultiValuedMap<Container, String> _map = new ArrayListValuedHashMap<>();
            {
                try (Stream<Container> stream = PropertyManager.getNormalStore().streamMatchingContainers(SITE_CONFIG_USER, LOOK_AND_FEEL_SET_NAME))
                {
                    stream.forEach(c -> {
                        String dateFormat = DateUtil.getDateFormatString(c);
                        if (!DateUtil.isStandardDateDisplayFormat(dateFormat))
                            _map.put(c, dateFormat);
                        String dateTimeFormat = DateUtil.getDateTimeFormatString(c);
                        if (!DateUtil.isStandardDateTimeDisplayFormat(dateTimeFormat))
                            _map.put(c, dateTimeFormat);
                        String timeFormat = DateUtil.getTimeFormatString(c);
                        if (!DateUtil.isStandardTimeDisplayFormat(timeFormat))
                            _map.put(c, timeFormat);
                    });
                }

                // TODO: Check folder settings
                // TODO: Check query XML
                // TODO: Check property descriptors
            }

            @Override
            public SiteValidationProviderFactory getFactory()
            {
                return DisplayFormatValidationProviderFactory.this;
            }

            @Override
            public @Nullable SiteValidationResultList runValidation(Container c, User u)
            {
                SiteValidationResultList results = new SiteValidationResultList();
                Collection<String> messages = _map.get(c);
                if (messages != null)
                    messages.forEach(results::addWarn);

                return results.nullIfEmpty();
            }
        };
    }
}
