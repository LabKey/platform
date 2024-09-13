package org.labkey.core.admin;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationProviderFactory;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;
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
            private final MultiValuedMap<Container, String> _defaultFormatsMap = new ArrayListValuedHashMap<>();
            private final MultiValuedMap<Container, QueryCandidate> _queryCandidatesMap = new ArrayListValuedHashMap<>();
            private final MultiValuedMap<Container, PropertyCandidate> _propertyCandidateMap = new ArrayListValuedHashMap<>();

            {
                try (Stream<Container> stream = PropertyManager.getNormalStore().streamMatchingContainers(SITE_CONFIG_USER, LOOK_AND_FEEL_SET_NAME))
                {
                    stream.forEach(c -> {
                        // Must get the stored values from LookAndFeelProperties since FolderSettingsCache holds inherited values
                        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
                        Arrays.stream(DateDisplayFormatType.values())
                            .forEach(type -> {
                                String format = type.getStoredFormat(laf);
                                if (format != null && !type.isStandardFormat(format))
                                    _defaultFormatsMap.put(c, c.getContainerNoun(true) + " default " + type.name().toLowerCase() + " format: " + format);
                        });
                    });
                }

                new SqlSelector(CoreSchema.getInstance().getSchema(), new SQLFragment("SELECT Container, \"schema\" AS SchemaName, Name AS QueryName FROM query.querydef WHERE metadata LIKE '%<formatString>%'"))
                    .stream(QueryCandidate.class)
                    .forEach(candidate -> _queryCandidatesMap.put(candidate.container(), candidate));

                // TODO: Join in more context -- e.g., what table?
                SQLFragment sql = new SQLFragment("SELECT Container, Name AS ColumnName, RangeURI, Format FROM " + OntologyManager.getTinfoPropertyDescriptor() + " WHERE Format IS NOT NULL AND RangeURI IN (?, ?, ?)")
                    .addAll(DateDisplayFormatType.getTypeUris());
                new SqlSelector(CoreSchema.getInstance().getSchema(), sql)
                    .stream(PropertyCandidate.class)
                    .filter(candidate -> {
                        DateDisplayFormatType type = DateDisplayFormatType.getForRangeUri(candidate.rangeUri());
                        return !type.isStandardFormat(candidate.format());
                    })
                    .forEach(candidate -> _propertyCandidateMap.put(candidate.container(), candidate));
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
                                    table.getColumns(columnsWithDisplayFormats)
                                        .forEach(column -> {
                                            DateDisplayFormatType type = DateDisplayFormatType.getForJdbcType(column.getJdbcType());
                                            if (type != null && !type.isStandardFormat(column.getFormat()))
                                                results.addWarn("Query metadata for " + column.getName() + " " + type.name().toLowerCase() + " column: " + column.getFormat());
                                        });
                                }
                            }
                        }
                    });

                _propertyCandidateMap.get(c)
                    .forEach(candidate -> results.addWarn("Property " + candidate.columnName() + ": " + candidate.format()));

                return results.nullIfEmpty();
            }
        };
    }

    private record QueryCandidate(Container container, String schemaName, String queryName) {}
    private record PropertyCandidate(Container container, String columnName, String rangeUri, String format) {}
}
