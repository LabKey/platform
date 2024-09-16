package org.labkey.core.admin;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
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
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TablesDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.labkey.api.settings.AbstractSettingsGroup.SITE_CONFIG_USER;
import static org.labkey.api.settings.LookAndFeelFolderProperties.LOOK_AND_FEEL_SET_NAME;

public class DisplayFormatAnalyzer
{
    public record DisplayFormatContext(String message, ActionURL url){}

    public interface DisplayFormatHandler
    {
        void handle(Container c, DateDisplayFormatType type, String format, Supplier<DisplayFormatContext> contextProvider);
    }

    private record NonStandardDefaultFormat(Container container, DateDisplayFormatType type, String format) {}
    private record QueryCandidate(Container container, String schemaName, String queryName) {}
    private record PropertyCandidate(Container container, String tableName, String columnName, String domainUri, String rangeUri, String format)
    {
        DateDisplayFormatType type()
        {
            return DateDisplayFormatType.getForRangeUri(rangeUri);
        }
    }

    private final MultiValuedMap<Container, NonStandardDefaultFormat> _defaultFormatsMap = new ArrayListValuedHashMap<>();
    private final MultiValuedMap<Container, QueryCandidate> _queryCandidatesMap = new ArrayListValuedHashMap<>();
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
                        if (format != null && !type.isStandardFormat(format))
                            _defaultFormatsMap.put(c, new NonStandardDefaultFormat(c, type, format));
                    });
            });
        }

        new SqlSelector(CoreSchema.getInstance().getSchema(), new SQLFragment("SELECT Container, \"schema\" AS SchemaName, Name AS QueryName FROM query.QueryDef WHERE metadata LIKE '%<formatString>%'"))
            .stream(QueryCandidate.class)
            .forEach(candidate -> _queryCandidatesMap.put(candidate.container(), candidate));

        SQLFragment sql = new SQLFragment(
                "SELECT dd.Container, DomainURI, dd.Name AS TableName, pd.Name AS ColumnName, RangeURI, Format FROM " + OntologyManager.getTinfoDomainDescriptor() + " dd\n" +
                "\tINNER JOIN " + OntologyManager.getTinfoPropertyDomain() + " pdm ON dd.DomainId = pdm.DomainId\n" +
                "\tINNER JOIN " + OntologyManager.getTinfoPropertyDescriptor() + " pd ON pdm.PropertyId = pd.PropertyId\n" +
                "\tWHERE Format IS NOT NULL AND RangeURI IN (?, ?, ?)")
            .addAll(DateDisplayFormatType.getTypeUris());
        new SqlSelector(CoreSchema.getInstance().getSchema(), sql)
            .stream(PropertyCandidate.class)
            .filter(candidate -> !candidate.type().isStandardFormat(candidate.format()))
            .forEach(candidate -> _propertyCandidateMap.put(candidate.container(), candidate));
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

        // First, inspect QueryDefinition to identify columns where XML has explicitly set a display format
        // (as opposed to inheriting a display format from another query or table definition). Then inspect
        // those columns via the TableInfo to determine date-time columns with non-standard formats.
        List<QueryException> errors = new ArrayList<>();
        _queryCandidatesMap.get(c).stream()
            .map(candidate -> _queryService.getQueryDef(user, c, candidate.schemaName, candidate.queryName))
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
                                        handler.handle(c, type, column.getFormat(),
                                            () -> new DisplayFormatContext(
                                                "Query metadata for \"" + table.getSchema().getDisplayName() + "." + table.getName() + "." + column.getName() + "\" " + type.name() + " column",
                                                _queryUrls.urlMetadataQuery(c, definition.getSchemaName(), definition.getName())
                                            )
                                        );
                                });
                        }
                    }
                }
            });

        _propertyCandidateMap.get(c)
            .forEach(candidate -> handler.handle(c, candidate.type(), candidate.format(),
                () -> {
                    GWTDomain<?> domain = DomainUtil.getDomainDescriptor(user, candidate.domainUri(), c);
                    return new DisplayFormatContext(
                        candidate.type().name() + " property \"" + domain.getSchemaName() + "." + domain.getQueryName() + "." + candidate.columnName() + "\"",
                            _queryUrls.urlSchemaBrowser(c, domain.getSchemaName(), domain.getQueryName())
                    );
                }));
    }

    public void handleAll(User user, DisplayFormatHandler handler)
    {
        Set<Container> containers = new HashSet<>(_defaultFormatsMap.keySet());
        containers.addAll(_queryCandidatesMap.keySet());
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
        P provider = PageFlowUtil.urlProvider(inter);
        if (provider == null)
            throw new IllegalStateException("No urlProvider found for " + inter.getName());

        return provider;
    }
}
