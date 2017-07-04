/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
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
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesType;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.data.xml.queryCustomView.LocalOrRefFiltersType;
import org.labkey.data.xml.queryCustomView.NamedFiltersType;
import org.labkey.query.persist.LinkedSchemaDef;

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


    public static LinkedSchema get(User user, Container container, LinkedSchemaDef def)
    {
        Container sourceContainer = def.lookupSourceContainer();
        if (sourceContainer == null)
        {
            Logger.getLogger(LinkedSchema.class).warn("Source container '" + def.getSourceContainerId() + "' not found for linked schema " + def.getUserSchemaName());
            return null;
        }

        TemplateSchemaType template = def.lookupTemplate(sourceContainer);
        String sourceSchemaName = def.getSourceSchemaName();
        if (sourceSchemaName == null && template != null)
            sourceSchemaName = template.getSourceSchemaName();
        UserSchema sourceSchema = getSourceSchema(def, sourceSchemaName, sourceContainer, user);
        if (sourceSchema == null)
        {
            Logger.getLogger(LinkedSchema.class).warn("Source schema '" + sourceSchemaName + "' not found in container '" + sourceContainer.getPath() + "' for linked schema " + def.getUserSchemaName());
            return null;
        }

        final Set<String> tableNames = sourceSchema.getTableNames();
        final Set<String> queryNames = sourceSchema.getQueryDefs().keySet();
        TableSource tableSource = new TableSource()
        {
            public Collection<String> getTableNames()         { return tableNames; }
            public Collection<String> getQueryNames()         { return queryNames; }
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

    private static UserSchema getSourceSchema(LinkedSchemaDef def, String sourceSchemaName, Container sourceContainer, User user)
    {
        SchemaKey sourceSchemaKey = SchemaKey.fromString(sourceSchemaName);

        // Disallow recursive linked schema
        if (def.lookupContainer() == sourceContainer && def.getUserSchemaName().equals(sourceSchemaName))
        {
            Logger.getLogger(LinkedSchema.class).warn("Disallowed recursive linked schema definition '" + sourceSchemaName + "' in container '" + sourceContainer.getPath() + "'");
            return null;
        }

        User sourceSchemaUser = new LinkedSchemaUserWrapper(user, sourceContainer);
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
    @NotNull
    public Map<String, QueryDefinition> getQueryDefs()
    {
        if (_availableQueries.size() == 0)
            return super.getQueryDefs();

        Map<String, QueryDefinition> queries =_sourceSchema.getQueryDefs();
        Map<String, QueryDefinition> ret = new CaseInsensitiveHashMap<>(queries.size());

        for (String key : queries.keySet())
        {
            if (_availableQueries.contains(key))
            {
                QueryDefinition queryDef = queries.get(key);
                TableInfo table = queryDef.getTable(new ArrayList<>(), true);
                if (table != null)
                {
                    // Apply any filters that might have been specified in the schema linking process
                    QueryDefinition filteredQueryDef = createWrapperQueryDef(key, table);

                    // Create a wrapper that knows how to apply the rest of the metadata correctly
                    LinkedSchemaQueryDefinition wrappedQueryDef = new LinkedSchemaQueryDefinition(this, filteredQueryDef);
                    ret.put(key, wrappedQueryDef);
                }
            }
        }

        // Get all the custom queries from the standard locations
        ret.putAll(super.getQueryDefs());

        return ret;
    }

    @Override
    protected TableInfo createSourceTable(String name)
    {
        return _sourceSchema.getTable(name);
    }

    @Override
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        QueryDefinition queryDef = createWrapperQueryDef(name, sourceTable);

        TableType metaData = getXbTable(name);
        ArrayList<QueryException> errors = new ArrayList<>();
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
        String sql = generateLabKeySQL(sourceTable, new XmlFilterWhereClauseSource(xmlFilters), parameterDecls);

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
        private final Container _sourceContainer;

        public LinkedSchemaUserWrapper(User realUser, Container sourceContainer)
        {
            super(realUser, realUser.getGroups(), Collections.singleton(RoleManager.getRole(ReaderRole.class)), false);
            _sourceContainer = sourceContainer;
        }

        @Override
        public Set<Role> getContextualRoles(SecurityPolicy policy)
        {
            // For the linked schema's source container, grant ReaderRole via the LimitedUser implementation
            if (policy.getContainerId().equals(_sourceContainer.getPolicy().getContainerId()))
            {
                return super.getContextualRoles(policy);
            }

            // For all other containers, just rely on normal permissions
            return Collections.emptySet();
        }
    }
}
