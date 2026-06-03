/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.admin;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.labkey.api.settings.AbstractSettingsGroup.SITE_CONFIG_USER;
import static org.labkey.api.settings.LookAndFeelFolderProperties.LOOK_AND_FEEL_SET_NAME;
import static org.labkey.api.util.DateUtil.META_FORMATS;

public class DisplayFormatAnalyzer
{
    private static final Logger LOG = LogHelper.getLogger(DisplayFormatAnalyzer.class, "Warnings during date format analysis");

    public record DisplayFormatContext(String message, ActionURL url){}

    public interface DisplayFormatHandler
    {
        void handle(Container c, DateDisplayFormatType type, String format, Supplier<DisplayFormatContext> contextProvider);
    }

    private record NonStandardDefaultFormat(Container container, DateDisplayFormatType type, String format) {}
    private record QueryCandidate(Container container, String schemaName, String queryName, int rowId) {}
    private record PropertyCandidate(Container container, String tableName, String columnName, String domainUri, String rangeUri, String format)
    {
        DateDisplayFormatType type()
        {
            return DateDisplayFormatType.getForRangeUri(rangeUri);
        }
    }

    private final MultiValuedMap<Container, NonStandardDefaultFormat> _defaultFormatsMap = new ArrayListValuedHashMap<>();
    private final MultiValuedMap<Container, QueryCandidate> _queryDefXmlCandidatesMap = new ArrayListValuedHashMap<>();
    private final MultiValuedMap<Container, PropertyCandidate> _propertyCandidateMap = new ArrayListValuedHashMap<>();

    private final AdminUrls _adminUrls = urlProvider(AdminUrls.class);
    private final QueryUrls _queryUrls = urlProvider(QueryUrls.class);
    private final QueryService _queryService = QueryService.get();

    public DisplayFormatAnalyzer()
    {
        try (Stream<Container> stream = PropertyManager.getNormalStore().streamMatchingContainers(SITE_CONFIG_USER, LOOK_AND_FEEL_SET_NAME))
        {
            stream.forEach(c -> {
                // Must get the stored values from LookAndFeelProperties since FolderSettingsCache holds inherited values
                LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
                Arrays.stream(DateDisplayFormatType.values())
                    .forEach(type -> {
                        String format = type.getStoredFormat(laf);
                        if (format != null && !type.isStandardFormat(format)) // Meta formats are not valid here
                            _defaultFormatsMap.put(c, new NonStandardDefaultFormat(c, type, format));
                    });
            });
        }

        new SqlSelector(CoreSchema.getInstance().getSchema(),
            new SQLFragment("SELECT Container, QueryDefId AS RowId, \"schema\" AS SchemaName, Name AS QueryName FROM query.QueryDef WHERE metadata LIKE '%<formatString>%'")
        )
            .stream(QueryCandidate.class)
            .forEach(candidate -> _queryDefXmlCandidatesMap.put(candidate.container(), candidate));

        SQLFragment sql = new SQLFragment(
                "SELECT dd.Container, DomainURI, dd.Name AS TableName, pd.Name AS ColumnName, RangeURI, Format FROM " + OntologyManager.getTinfoDomainDescriptor() + " dd\n" +
                "\tINNER JOIN " + OntologyManager.getTinfoPropertyDomain() + " pdm ON dd.DomainId = pdm.DomainId\n" +
                "\tINNER JOIN " + OntologyManager.getTinfoPropertyDescriptor() + " pd ON pdm.PropertyId = pd.PropertyId\n" +
                "\tWHERE Format IS NOT NULL AND RangeURI IN (?, ?, ?)")
            .addAll(DateDisplayFormatType.getTypeUris());
        new SqlSelector(CoreSchema.getInstance().getSchema(), sql)
            .stream(PropertyCandidate.class)
            .filter(candidate -> isNonStandardFormat(candidate.type(), candidate.format()))
            .forEach(candidate -> _propertyCandidateMap.put(candidate.container(), candidate));
    }

    // Allows standard formats and standard meta formats
    private boolean isNonStandardFormat(DateDisplayFormatType type, String format)
    {
        return !type.isStandardFormat(format) && !META_FORMATS.contains(format);
    }

    public void handle(Container c, User user, DisplayFormatHandler handler)
    {
        _defaultFormatsMap.get(c)
            .forEach(defaultFormat -> handler.handle(defaultFormat.container(), defaultFormat.type(), defaultFormat.format(),
                () -> new DisplayFormatContext(
                    (c.isRoot() ? "Site" : c.getContainerNoun(true)) + " default display format for " + defaultFormat.type().name() + "s",
                    _adminUrls.getLookAndFeelSettingsURL(c)
                )
            ));

        // First, inspect QueryDef metadata to find columns where XML has explicitly set a display format
        // (as opposed to inheriting a display format from another query or table definition). Then inspect
        // those columns via the TableInfo to find date-time columns with non-standard formats.
        _queryDefXmlCandidatesMap.get(c)
            .forEach(candidate -> {
                TableType tableType = _queryService.getQueryDefMetadata(c, candidate.rowId());

                if (tableType != null)
                {
                    ColumnType[] columnTypes = tableType.getColumns().getColumnArray();
                    String[] columnsWithDisplayFormats = Arrays.stream(columnTypes)
                        .filter(ColumnType::isSetFormatString)
                        .map(ColumnType::getColumnName)
                        .toArray(String[]::new);

                    if (columnsWithDisplayFormats.length > 0)
                    {
                        QuerySchema schema = DefaultSchema.get(user, c).getSchema(candidate.schemaName());
                        if (schema != null)
                        {
                            try
                            {
                                TableInfo table = schema.getTable(candidate.queryName());
                                if (table != null)
                                {
                                    table.getColumns(columnsWithDisplayFormats)
                                        .forEach(column -> {
                                            DateDisplayFormatType type = DateDisplayFormatType.getForJdbcType(column.getJdbcType());
                                            if (type != null && isNonStandardFormat(type, column.getFormat()))
                                                handler.handle(c, type, column.getFormat(),
                                                    () -> new DisplayFormatContext(
                                                        "Metadata for \"" + table.getSchema().getDisplayName() + "." + table.getName() + "." + column.getName() + "\" " + type.name() + " column",
                                                        _queryUrls.urlMetadataQuery(c, schema.getName(), table.getName())
                                                    )
                                                );
                                        });
                                }
                            }
                            catch (Exception e)
                            {
                                // likely query parsing problem - skip this query/table
                            }
                        }
                    }
                }
            });

        _propertyCandidateMap.get(c)
            .forEach(candidate ->
                {
                    try
                    {
                        GWTDomain<?> domain = DomainUtil.getDomainDescriptor(user, candidate.domainUri(), c);
                        if (domain == null)
                        {
                            LOG.warn("Could not find domain for {}.{} ({})", candidate.tableName(), candidate.columnName(), candidate.domainUri());
                        }
                        else
                        {
                            handler.handle(c, candidate.type(), candidate.format(),
                                () -> new DisplayFormatContext(
                                    candidate.type().name() + " property \"" + domain.getSchemaName() + "." + domain.getQueryName() + "." + candidate.columnName() + "\"",
                                    _queryUrls.urlSchemaBrowser(c, domain.getSchemaName(), domain.getQueryName())
                                ));
                        }
                    }
                    catch (Exception e)
                    {
                        // Best effort -- skip if domainUri is unrecognized or other exception occurs, Issue 53488
                        LOG.warn("Exception while attempting to analyze {}.{}", candidate.tableName(), candidate.columnName(), e);
                    }
                }
            );
    }

    public void handleAll(User user, DisplayFormatHandler handler)
    {
        Set<Container> containers = new HashSet<>(_defaultFormatsMap.keySet());
        containers.addAll(_queryDefXmlCandidatesMap.keySet());
        containers.addAll(_propertyCandidateMap.keySet());

        containers.forEach(c -> handle(c, user, handler));
    }

    public static UsageMetricsProvider getMetricsProvider()
    {
        // Collect the unique set of non-standard date display formats across the entire site
        Set<String> badFormats = new HashSet<>();
        return () -> {
            DisplayFormatAnalyzer analyzer = new DisplayFormatAnalyzer();
            analyzer.handleAll(User.getAdminServiceUser(), (c, type, format, contextProvider) -> badFormats.add(format));
            return Map.of("nonStandardDateDisplayFormats", badFormats);
        };
    }

    private <P extends UrlProvider> @NotNull P urlProvider(Class<P> inter)
    {
        return PageFlowUtil.urlProvider(inter);
    }
}
