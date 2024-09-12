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
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TablesDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
            private final MultiValuedMap<Container, QueryCandidate> _queryCandidatesMap = new ArrayListValuedHashMap<>();

            {
                try (Stream<Container> stream = PropertyManager.getNormalStore().streamMatchingContainers(SITE_CONFIG_USER, LOOK_AND_FEEL_SET_NAME))
                {
                    stream.forEach(c -> {
                        // Must get the stored values from LookAndFeelProperties since FolderSettingsCache holds inherited values
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

                new SqlSelector(CoreSchema.getInstance().getSchema(), new SQLFragment("SELECT Container, \"schema\", Name FROM query.querydef WHERE metadata LIKE '%<formatString>%'"))
                    .mapStream()
                    .forEach(map -> _queryCandidatesMap.put(ContainerManager.getForId((String)map.get("Container")), new QueryCandidate((String)map.get("Schema"), (String)map.get("Name"))));

                // TODO: Query property descriptors
            }

            @Override
            public SiteValidationProviderFactory getFactory()
            {
                return DisplayFormatValidationProviderFactory.this;
            }

            @Override
            public @Nullable SiteValidationResultList runValidation(Container c, User u)
            {
                // TODO: Add links and context to warnings

                SiteValidationResultList results = new SiteValidationResultList();
                Collection<String> messages = _defaultFormatsMap.get(c);
                if (messages != null)
                    messages.forEach(results::addWarn);

                // First, inspect QueryDefinition to identify columns where XML has explicitly set a display format
                // (as opposed to inheriting a display format from another query or table definition). Then inspect
                // those columns from the TableInfo to determine date-time columns with non-standard formats.
                QueryService qs = QueryService.get();
                List<QueryException> errors = new ArrayList<>();
                _queryCandidatesMap.get(c).stream()
                    .map(candidate -> qs.getQueryDef(u, c, candidate.schemaName, candidate.queryName))
                    .forEach(definition -> {
                        TablesDocument doc = definition.getMetadataTablesDocument();
                        if (doc != null)
                        {
                            ColumnType[] columnTypes = doc.getTables().getTableArray()[0].getColumns().getColumnArray();
                            String[] columnsWithDisplayFormats = Arrays.stream(columnTypes)
                                .filter(ColumnType::isSetFormatString)
                                .map(ColumnType::getColumnName)
                                .toArray(String[]::new);

                            if (columnsWithDisplayFormats.length > 0)
                            {
                                TableInfo table = definition.getTable(errors, true);
                                if (table != null)
                                {
                                    table.getColumns(columnsWithDisplayFormats).stream()
                                        .filter(column ->
                                            column.getJdbcType() == JdbcType.DATE && !DateUtil.isStandardDateDisplayFormat(column.getFormat()) ||
                                            column.getJdbcType() == JdbcType.TIMESTAMP && !DateUtil.isStandardDateTimeDisplayFormat(column.getFormat()) ||
                                            column.getJdbcType() == JdbcType.TIME && !DateUtil.isStandardTimeDisplayFormat(column.getFormat()))
                                        .forEach(column -> results.addWarn(column.getName() + " " + column.getFormat()));
                                }
                            }
                        }
                    });

                return results.nullIfEmpty();
            }
        };
    }

    private record QueryCandidate(String schemaName, String queryName) {}
}
