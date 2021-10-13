/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.LinkedSchemaCustomizer;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UserSchemaCustomizer;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesType;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.data.xml.queryCustomView.LocalOrRefFiltersType;
import org.labkey.data.xml.queryCustomView.NamedFiltersType;
import org.labkey.query.persist.LinkedSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: kevink
 * Date: 12/10/12
 */
public class LinkedSchema extends ExternalSchema
{
    public static void register()
    {
        DefaultSchema.registerProvider(new DefaultSchema.DynamicSchemaProvider()
        {
            @Override
            public QuerySchema getSchema(User user, Container container, String name)
            {
                QueryServiceImpl svc = (QueryServiceImpl)QueryService.get();
                return svc.getLinkedSchema(user, container, name);
            }

            @NotNull
            @Override
            public Collection<String> getSchemaNames(User user, Container container)
            {
                QueryServiceImpl svc = (QueryServiceImpl) QueryService.get();
                return svc.getLinkedSchemas(user, container).keySet();
            }
        });
    }

    private final UserSchema _sourceSchema;
    private final Collection<String> _availableQueries;
    private final Map<String, QueryDefinition> _resolvedQueries = new CaseInsensitiveHashMap<>();
    private boolean _resolvedAllQueries = false;


    public static LinkedSchema get(User user, Container container, LinkedSchemaDef def)
    {
        Container sourceContainer = def.lookupSourceContainer();
        if (sourceContainer == null)
        {
            LogManager.getLogger(LinkedSchema.class).warn("Source container '" + def.getSourceContainerId() + "' not found for linked schema " + def.getUserSchemaName());
            return null;
        }

        TemplateSchemaType template = def.lookupTemplate(sourceContainer);
        String sourceSchemaName = def.getSourceSchemaName();
        if (sourceSchemaName == null && template != null)
            sourceSchemaName = template.getSourceSchemaName();

        // Disallow recursive linked schema
        if (def.lookupContainer() == sourceContainer && def.getUserSchemaName().equals(sourceSchemaName))
        {
            LogManager.getLogger(LinkedSchema.class).warn("Disallowed recursive linked schema definition '" + sourceSchemaName + "' in container '" + sourceContainer.getPath() + "'");
            return null;
        }

        // Create the source schema with the user that created the linked schema.
        // The linked schema queries will be executed with the same permission as the the linked schema creator.
        // CONSIDER: Add an "execute as" setting on the LinkedSchemaDef to specify a different user.
        User sourceSchemaUser = UserManager.getUser(def.getCreatedBy());
        if (sourceSchemaUser == null)
        {
            LogManager.getLogger(LinkedSchema.class).warn("Source schema user '" + def.getCreatedBy() + "' not found");
            return null;
        }

        UserSchema sourceSchema = getSourceSchema(sourceSchemaName, sourceContainer, sourceSchemaUser);
        if (sourceSchema == null)
        {
            LogManager.getLogger(LinkedSchema.class).warn("Source schema '" + sourceSchemaName + "' not found in container '" + sourceContainer.getPath() + "' for linked schema " + def.getUserSchemaName());
            return null;
        }

        final Set<String> tableNames = sourceSchema.getTableNames();
        final Set<String> queryNames = sourceSchema.getQueryDefs().keySet();
        TableSource tableSource = new TableSource()
        {
            @Override
            public Collection<String> getTableNames()         { return tableNames; }
            @Override
            public Collection<String> getQueryNames()         { return queryNames; }
            @Override
            public boolean isTableAvailable(String tableName) { return tableNames.contains(tableName); }
        };

        TablesType tablesType = parseTablesType(def, template);

        NamedFiltersType[] namedFilters = null;
        TableType[] tableTypes = null;
        Collection<UserSchemaCustomizer> schemaCustomizers = null;
        if (tablesType != null)
        {
            namedFilters = tablesType.getFiltersArray();
            tableTypes = tablesType.getTableArray();
            schemaCustomizers = UserSchemaCustomizer.Factory.create(tablesType.getSchemaCustomizerArray());
        }

        // CONSIDER: Customize metadata and parameters once before construct?
        Map<String, TableType> metaDataMap = getMetaDataMap(tableTypes);
        Collection<String> availableTables = getAvailableTables(def, template, tableSource, metaDataMap);

        // Gathering the hidden tables requires looking at the original schema's hidden/visible tables
        // and then using the additional table metadata supplied.
        Set<String> hiddenTables = new CaseInsensitiveHashSet(availableTables);
        hiddenTables.removeAll(sourceSchema.getVisibleTableNames());

        hiddenTables.addAll(getHiddenTables(tableTypes));

        Collection<String> availableQueries = getAvailableQueries(def, template, tableSource);

        return new LinkedSchema(user, container, def, template, sourceSchema, metaDataMap, namedFilters, schemaCustomizers, availableTables, hiddenTables, availableQueries);
    }

    private static UserSchema getSourceSchema(String sourceSchemaName, Container sourceContainer, User sourceSchemaUser)
    {
        SchemaKey sourceSchemaKey = SchemaKey.fromString(sourceSchemaName);

        return QueryService.get().getUserSchema(sourceSchemaUser, sourceContainer, sourceSchemaKey);
    }

    private LinkedSchema(User user, Container container, LinkedSchemaDef def, TemplateSchemaType template, UserSchema sourceSchema,
                         Map<String, TableType> metaDataMap,
                         NamedFiltersType[] namedFilters,
                         Collection<UserSchemaCustomizer> schemaCustomizers,
                         Collection<String> availableTables,
                         Collection<String> hiddenTables,
                         Collection<String> availableQueries)
    {
        super(user, container, def, template, sourceSchema.getDbSchema(), metaDataMap, namedFilters, schemaCustomizers, availableTables, hiddenTables);

        _sourceSchema = sourceSchema;
        _availableQueries = availableQueries;
    }

    public UserSchema getSourceSchema()
    {
        return _sourceSchema;
    }

    @Override
    public Set<String> getVisibleTableNames()
    {
        if (_visible == null)
        {
            Set<String> availableTableNames = getTableNames();

            _visible = new CaseInsensitiveTreeSet();
            for (String tableName : _sourceSchema.getVisibleTableNames())
                if (availableTableNames.contains(tableName))
                    _visible.add(tableName);
        }
        return _visible;
    }

    @Override
    public LinkedTableInfo createTable(String name, ContainerFilter cf)
    {
        return (LinkedTableInfo)super.createTable(name, cf);
    }

    @Override
    public @Nullable QueryDefinition getQueryDef(@NotNull String queryName)
    {
        return getQueryDefs(queryName).get(queryName);
    }

    @Override
    @NotNull
    public Map<String, QueryDefinition> getQueryDefs()
    {
        return getQueryDefs(null);
    }

    /** @param nameFilter if non-null, only create the QueryDefinition for the requested name, to avoid needing
     * to create them for a bunch of queries the caller doesn't care about */
    @NotNull
    public Map<String, QueryDefinition> getQueryDefs(@Nullable String nameFilter)
    {
        if (_availableQueries.size() == 0)
            return super.getQueryDefs();

        if (nameFilter == null && _resolvedAllQueries)
        {
            return _resolvedQueries;
        }
        if (nameFilter != null && _resolvedQueries.containsKey(nameFilter))
        {
            return _resolvedQueries;
        }

        Map<String, QueryDefinition> queries = new HashMap<>();
        if (nameFilter == null)
        {
            queries.putAll(_sourceSchema.getQueryDefs());
        }
        else
        {
            queries.put(nameFilter, _sourceSchema.getQueryDef(nameFilter));
        }

        Map<String, QueryDefinition> ret = new CaseInsensitiveHashMap<>(queries.size());

        for (String key : queries.keySet())
        {
            if (_availableQueries.contains(key) && (nameFilter == null || nameFilter.equalsIgnoreCase(key)))
            {
                QueryDefinition queryDef = queries.get(key);
                var sourceErrors = new ArrayList<QueryException>();
                TableInfo table = queryDef.getTable(sourceErrors, true);
                if (table != null)
                {
                    // Apply any filters that might have been specified in the schema linking process
                    QueryDefinition filteredQueryDef = createWrapperQueryDef(key, table);
                    assert filteredQueryDef.getMetadataXml() == null : "generated wrapped query def shouldn't apply metadata xml";

                    // Get metadata override xml that may exist in the linked schema target container
                    String extraMetadata = null;
                    QueryDef extraMetadataQueryDef = QueryManager.get().getQueryDef(getContainer(), this.getSchemaName(), filteredQueryDef.getName(), false);
                    if (extraMetadataQueryDef != null)
                    {
                        assert extraMetadataQueryDef.getSql() == null : "metadata only querydef should not have sql";
                        extraMetadata = extraMetadataQueryDef.getMetaData();
                    }

                    // Create a wrapper that knows how to apply the rest of the metadata correctly
                    LinkedSchemaQueryDefinition wrappedQueryDef = new LinkedSchemaQueryDefinition(this, filteredQueryDef, extraMetadata);
                    ret.put(key, wrappedQueryDef);
                }
                else if (!sourceErrors.isEmpty())
                {
                    // Issue 43860: Stash the source query errors to report them later
                    LinkedSchemaQueryDefinition wrappedQueryDef = new LinkedSchemaQueryDefinition(this, sourceErrors, key);
                    ret.put(key, wrappedQueryDef);
                }
            }
        }

        // Get all the custom queries from the standard locations
        ret.putAll(super.getQueryDefs());

        _resolvedQueries.putAll(ret);
        if (nameFilter == null)
        {
            _resolvedAllQueries = true;
        }

        return ret;
    }

    @Override
    protected TableInfo createSourceTable(String name)
    {
        return _sourceSchema.getTable(name, null, true, true);
    }

    @Override
    public @NotNull ContainerFilter getDefaultContainerFilter()
    {
        return super.getDefaultContainerFilter();
        // NOTE: The source table ContainerFilter is already set by _sourceSchema.getTable()
//        return new ContainerFilter.InternalNoContainerFilter(getUser());
    }

    @Override
    protected LinkedTableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable, ContainerFilter cf)
    {
        TableType metaData = getXbTable(name);
        ArrayList<QueryException> errors = new ArrayList<>();
        if (null != metaData && sourceTable.isMetadataOverrideable())
            sourceTable.overlayMetadata(Collections.singletonList(metaData), _sourceSchema, errors);

        QueryDefinition queryDef = createWrapperQueryDef(name, sourceTable);
        assert queryDef.getMetadataXml() == null : "generated wrapped query def shouldn't apply metadata xml";
        // Hand in entire metaDataMap (of tables) into queryDef. Then when Query.resolveTable binds to a table, apply the metadata if it exists.
        queryDef.setMetadataTableMap(_metaDataMap);

        TableInfo tableInfo = queryDef.getTable(errors, true);
        if (!errors.isEmpty())
        {
            for (QueryException ex : errors)
            {
                if (ex instanceof QueryParseException && ((QueryParseException)ex).isWarning())
                    continue;
                throw ex;
            }
        }
        if (tableInfo == null)
        {
            return null;
        }

        Set<FieldKey> includedFields = null;
        if (null != metaData && null != metaData.getIncludeColumnsList())
        {
            String cols = StringUtils.trim(metaData.getIncludeColumnsList());
            if (!StringUtils.equals("*",cols))
            {
                includedFields = Arrays.asList(StringUtils.split(cols,";")).stream()
                        .map((col)->new FieldKey(null,col))
                        .collect(Collectors.toSet());
            }
        }

        // Copy properties from source table to query table
        tableInfo.setDefaultVisibleColumns(sourceTable.getDefaultVisibleColumns());

        // make sure to filter out columns that may be hidden by the extra metadata that may be set later.
        // see issue 28533
        tableInfo.setDefaultVisibleColumns(() -> sourceTable.getDefaultVisibleColumns().stream().filter(fieldKey -> {
            ColumnInfo column = tableInfo.getColumn(fieldKey);
            return (null == column) || !tableInfo.getColumn(fieldKey).isHidden();
        }).iterator());


        LinkedTableInfo linkedTableInfo = new LinkedTableInfo(this, tableInfo, includedFields, null);
        linkedTableInfo.init();

        // Apply metadata. Filters have already been applied
        linkedTableInfo.loadFromXML(this, Collections.singleton(metaData), errors);

        return linkedTableInfo;
    }

    /** Wraps the TableInfo with a custom query that applies any filters to limit the rows */
    private QueryDefinition createWrapperQueryDef(String name, TableInfo sourceTable)
    {
        assert !(sourceTable instanceof SchemaTableInfo) : "LinkedSchema only wraps query TableInfos, not SchemaTableInfos";

        TableType metaData = getXbTable(name);
        LocalOrRefFiltersType xmlFilters = metaData != null ? metaData.getFilters() : null;
        Collection<QueryService.ParameterDecl> parameterDecls = new ArrayList<>();

        // UNDONE: Don't let the filters be mutated -- use a filter type instead of xmlbeans
        xmlFilters = fireCustomizeFilters(name, sourceTable, xmlFilters);
        if (metaData == null && xmlFilters != null)
        {
            metaData = TableType.Factory.newInstance();
            metaData.setFilters(xmlFilters);
        }
        parameterDecls = fireCustomizeParameters(name, sourceTable, xmlFilters, parameterDecls);

        return createQueryDef(name, sourceTable, xmlFilters, parameterDecls);
    }

    /** Build up LabKey SQL that targets the desired container and appends any WHERE clauses (but not URL-style filters) */
    private QueryDefinition createQueryDef(String name, TableInfo sourceTable, @Nullable LocalOrRefFiltersType xmlFilters, Collection<QueryService.ParameterDecl> parameterDecls)
    {
        // Issue 40442: LinkedSchema should throw handled exception with bad configuration
        // IllegalArgumentException can be thrown when generating LabKey SQL for a filter over a column that doesn't exist.
        String sql;
        try
        {
            sql = generateLabKeySQL(sourceTable, new XmlFilterWhereClauseSource(xmlFilters), parameterDecls);
        }
        catch (IllegalArgumentException e)
        {
            throw new QueryParseException("Error creating linked schema table '" + name + "': " + e.getMessage(), e, 0, 0);
        }

        QueryDefinition queryDef = QueryServiceImpl.get().createQueryDef(_sourceSchema.getUser(), _sourceSchema.getContainer(), _sourceSchema, name);
        queryDef.setSql(sql);
        return queryDef;
    }

    public interface SQLWhereClauseSource
    {
        List<String> getWhereClauses(TableInfo sourceTable);
    }

    private class XmlFilterWhereClauseSource implements SQLWhereClauseSource
    {
        private final LocalOrRefFiltersType _xmlFilters;

        private XmlFilterWhereClauseSource(LocalOrRefFiltersType xmlFilters)
        {
            _xmlFilters = xmlFilters;
        }

        @Override
        public List<String> getWhereClauses(TableInfo sourceTable)
        {
            List<String> result = new ArrayList<>();
            if (_xmlFilters != null)
            {
                if (_xmlFilters.isSetRef())
                {
                    // Add the WHERE from the referenced, shared filter
                    NamedFiltersType filter = _namedFilters.get(_xmlFilters.getRef());
                    if (filter != null)
                    {
                        if (filter.sizeOfWhereArray() > 0)
                        {
                            result.addAll(Arrays.asList(filter.getWhereArray()));
                        }
                        if (filter.getFilterArray() != null)
                        {
                            SimpleFilter simpleFilter = SimpleFilter.fromXml(filter.getFilterArray());
                            if (simpleFilter != null)
                            {
                                result.add(simpleFilter.toLabKeySQL(QueryService.get().getColumns(sourceTable, simpleFilter.getAllFieldKeys())));
                            }
                        }
                    }
                }
                else
                {
                    // Add the filters that are specific to this table
                    result.addAll(Arrays.asList(_xmlFilters.getWhereArray()));
                    SimpleFilter simpleFilter = SimpleFilter.fromXml(_xmlFilters.getFilterArray());
                    if (simpleFilter != null)
                    {
                        result.add(simpleFilter.toLabKeySQL(QueryService.get().getColumns(sourceTable, simpleFilter.getAllFieldKeys())));
                    }
                }
            }
            return result;
        }
    }

    public static String generateLabKeySQL(TableInfo sourceTable, SQLWhereClauseSource whereClauseSource, Collection<QueryService.ParameterDecl> parameterDecls)
    {
        if (null == sourceTable.getUserSchema())
            throw new QueryException("Source table '" + sourceTable.getName() + "' has no user schema");
        if (null == sourceTable.getUserSchema().getContainer())
            throw new QueryException("Source table '" + sourceTable.getName() + "' has no container");

        StringBuilder sql = new StringBuilder();

        if (parameterDecls != null && !parameterDecls.isEmpty())
        {
            String paramSep = "";
            sql.append("PARAMETERS (\n");
            for (QueryService.ParameterDecl decl : parameterDecls)
            {
                sql.append(paramSep);
                sql.append("  \"").append(decl.getName()).append("\" ").append(decl.getJdbcType().name());
                if (decl.getDefault() != null)
                    sql.append(" DEFAULT ").append(decl.getDefault());
                paramSep = ",\n";
            }
            sql.append(")\n");
        }

        sql.append("SELECT\n");

        String columnSep = "";
        for (ColumnInfo col : sourceTable.getColumns())
        {
            if (col.isAdditionalQueryColumn())
                continue;
            sql.append(columnSep);
            sql.append("\"").append(col.getName()).append("\"");
            if (col.isHidden())
                sql.append(" @hidden");

            columnSep = ", ";
        }
        sql.append(" FROM \"");
        sql.append(sourceTable.getUserSchema().getContainer().getPath());
        sql.append("\".");
        sql.append(sourceTable.getUserSchema().getSchemaPath().toSQLString());
        sql.append(".\"");
        sql.append(sourceTable.getName());
        sql.append("\"\n");

        // Apply LabKey SQL <where> style filters and <filter> style filters.
        List<String> whereClauses = whereClauseSource.getWhereClauses(sourceTable);
        if (!whereClauses.isEmpty())
        {
            String filterSep = " WHERE ";

            for (String whereClause : whereClauses)
            {
                sql.append(filterSep);
                filterSep = " AND ";
                sql.append("(");
                sql.append(whereClause);
                sql.append(")");
            }
        }

        return sql.toString();
    }

    protected LocalOrRefFiltersType fireCustomizeFilters(String name, TableInfo table, @Nullable LocalOrRefFiltersType xmlFilters)
    {
        if (_schemaCustomizers != null)
        {
            for (UserSchemaCustomizer customizer : _schemaCustomizers)
                if (customizer instanceof LinkedSchemaCustomizer)
                    xmlFilters = ((LinkedSchemaCustomizer) customizer).customizeFilters(name, table, xmlFilters);
        }
        return xmlFilters;
    }

    protected Collection<QueryService.ParameterDecl> fireCustomizeParameters(String name, TableInfo table, @Nullable LocalOrRefFiltersType xmlFilters, Collection<QueryService.ParameterDecl> parameterDecls)
    {
        parameterDecls = new ArrayList<>(parameterDecls);
        if (_schemaCustomizers != null)
        {
            for (UserSchemaCustomizer customizer : _schemaCustomizers)
                if (customizer instanceof LinkedSchemaCustomizer)
                    parameterDecls.addAll(((LinkedSchemaCustomizer) customizer).customizeParameters(name, table, xmlFilters));
        }
        return parameterDecls;
    }

    protected Map<String, Object> fireCustomizeParameterValues(LinkedTableInfo table)
    {
        Map<String, Object> paramValues = new HashMap<>();
        if (_schemaCustomizers != null)
        {
            for (UserSchemaCustomizer customizer : _schemaCustomizers)
                if (customizer instanceof LinkedSchemaCustomizer)
                    paramValues.putAll(((LinkedSchemaCustomizer) customizer).customizeParamValues(table));
        }
        return paramValues;
    }

    private static class LinkedSchemaUserWrapper extends LimitedUser
    {
        private final Set<String> _allowedContainerIds;

        public LinkedSchemaUserWrapper(User realUser, Set<Container> dependentContainers)
        {
            super(realUser, realUser.getGroups(), Collections.singleton(RoleManager.getRole(ReaderRole.class)), false);

            // Get the policy container id for the dependent containers
            _allowedContainerIds = dependentContainers.stream().map(Container::getPolicy).map(SecurityPolicy::getContainerId).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public Set<Role> getContextualRoles(SecurityPolicy policy)
        {
            // For the linked schema's source container and any dependent containers,
            // grant ReaderRole via the LimitedUser implementation.
            if (_allowedContainerIds.contains(policy.getContainerId()))
            {
                return super.getContextualRoles(policy);
            }

            // For all other containers, just rely on normal permissions
            return Collections.emptySet();
        }
    }
}
