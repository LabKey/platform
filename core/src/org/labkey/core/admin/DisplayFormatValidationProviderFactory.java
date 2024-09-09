package org.labkey.core.admin;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationProviderFactory;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.stream.Stream;

import static org.labkey.api.settings.AbstractSettingsGroup.SITE_CONFIG_USER;
import static org.labkey.api.settings.LookAndFeelFolderProperties.LOOK_AND_FEEL_SET_NAME;

public class DisplayFormatValidationProviderFactory implements SiteValidationProviderFactory
{
    private static final Logger LOG = LogHelper.getLogger(DisplayFormatValidationProviderFactory.class, "Validator debugging");

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
            private final MultiValuedMap<Container, String> _defaultFormatsMap = new ArrayListValuedHashMap<>();
            private final MultiValuedMap<Container, Pair<String, String>> _queryCandidatesMap = new ArrayListValuedHashMap<>();

            {
                try (Stream<Container> stream = PropertyManager.getNormalStore().streamMatchingContainers(SITE_CONFIG_USER, LOOK_AND_FEEL_SET_NAME))
                {
                    stream.forEach(c -> {
                        // Must get the stored values from LookAndFeelProperties; FolderSettingsCache holds inherited values
                        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
                        String dateFormat = laf.getDefaultDateFormatStored();
                        if (dateFormat != null && !DateUtil.isStandardDateDisplayFormat(dateFormat))
                            _defaultFormatsMap.put(c, dateFormat);
                        String dateTimeFormat = laf.getDefaultDateTimeFormatStored();
                        if (dateTimeFormat != null && !DateUtil.isStandardDateTimeDisplayFormat(dateTimeFormat))
                            _defaultFormatsMap.put(c, dateTimeFormat);
                        String timeFormat = laf.getDefaultTimeFormatStored();
                        if (timeFormat != null && !DateUtil.isStandardTimeDisplayFormat(timeFormat))
                            _defaultFormatsMap.put(c, timeFormat);
                    });
                }

                // TODO: Add links and context to warnings

                // TODO: Stream to a record?
                DbSchema dbSchema = CoreSchema.getInstance().getSchema();
                new SqlSelector(dbSchema, new SQLFragment("SELECT Container, \"schema\", Name FROM query.querydef WHERE metadata LIKE '%<formatString>%'"))
                    .mapStream()
                    .forEach(map -> _queryCandidatesMap.put(ContainerManager.getForId((String)map.get("Container")), Pair.of((String)map.get("Schema"), (String)map.get("Name"))));

                // TODO: Handle query XML

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
                Collection<String> messages = _defaultFormatsMap.get(c);
                if (messages != null)
                    messages.forEach(results::addWarn);

                _queryCandidatesMap.get(c).stream()
                    .map(pair -> QueryService.get().getQueryDef(u, c, pair.first, pair.second))
                    .forEach(queryDef -> LOG.info(queryDef));

                return results.nullIfEmpty();
            }
        };
    }
}
