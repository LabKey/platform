/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    protected final Collection<String> _availableQueries;

    public static LinkedSchema get(User user, Container container, LinkedSchemaDef def)
    {
        Container sourceContainer = def.lookupSourceContainer();
        if (sourceContainer == null)
            return null;

        TemplateSchemaType template = def.lookupTemplate(sourceContainer);
        UserSchema sourceSchema = getSourceSchema(def, template, sourceContainer, user);
        if (sourceSchema == null)
            return null;

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
        if (tablesType != null)
        {
            namedFilters = tablesType.getFiltersArray();
            tableTypes = tablesType.getTableArray();
        }

        Map<String, TableType> metaDataMap = getMetaDataMap(tableTypes);
        Collection<String> availableTables = getAvailableTables(def, template, tableSource, metaDataMap);

        // Gathering the hidden tables requires looking at the original schema's hidden/visible tables
        // and then using the additional table metadata supplied.
        Set<String> hiddenTables = new CaseInsensitiveHashSet(availableTables);
        //Issue 17492: CaseInsensitiveHashSet.removeAll fails to remove items
        //hiddenTables.removeAll(sourceSchema.getVisibleTableNames());
       for (String visibleTable : sourceSchema.getVisibleTableNames())
            hiddenTables.remove(visibleTable);

        hiddenTables.addAll(getHiddenTables(tableTypes));

        Collection<String> availableQueries = getAvailableQueries(def, template, tableSource);

        return new LinkedSchema(user, container, def, template, sourceSchema, metaDataMap, namedFilters, availableTables, hiddenTables, availableQueries);
    }

    private static UserSchema getSourceSchema(LinkedSchemaDef def, TemplateSchemaType template, Container sourceContainer, User user)
    {
        String sourceSchemaName = def.getSourceSchemaName();
        if (sourceSchemaName == null && template != null)
            sourceSchemaName = template.getSourceSchemaName();
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
                         Collection<String> availableTables,
                         Collection<String> hiddenTables,
                         Collection<String> availableQueries)
    {
        super(user, container, def, template, sourceSchema.getDbSchema(), metaDataMap, namedFilters, availableTables, hiddenTables);

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
    public Map<String, QueryDefinition> getQueryDefs()
    {
        if (_availableQueries.size() == 0)
            return super.getQueryDefs();

        Map<String, QueryDefinition> queries =_sourceSchema.getQueryDefs();
        Map<String, QueryDefinition> ret = new CaseInsensitiveHashMap<QueryDefinition>(queries.size());

        for (String key : queries.keySet())
        {
            if (_availableQueries.contains(key))
            {
                QueryDefinition queryDef = queries.get(key);
                LinkedSchemaQueryDefinition wrappedQueryDef = new LinkedSchemaQueryDefinition(this, queryDef);
                ret.put(key, wrappedQueryDef);
            }
        }

        // Get all the custom queries from the standard locations
        ret.putAll(super.getQueryDefs());

        return ret;
    }

    @Override
    protected TableInfo createTable(String name)
    {
        TableInfo table = super.createTable(name);

        // fixup FKs, URLs

        return table;
    }

    @Override
    protected TableInfo createSourceTable(String name)
    {
        return _sourceSchema.getTable(name);
    }

    @Override
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        assert !(sourceTable instanceof SchemaTableInfo) : "LinkedSchema only wraps query TableInfos, not SchemaTableInfos";

        TableType metaData = getXbTable(name);
        QueryDefinition queryDef = createQueryDef(name, sourceTable, metaData);

        ArrayList<QueryException> errors = new ArrayList<QueryException>();
        TableInfo tableInfo = queryDef.getTable(errors, true);
        if (!errors.isEmpty())
        {
            throw errors.get(0);
        }
        if (tableInfo == null)
        {
            return null;
        }

        LinkedTableInfo linkedTableInfo = new LinkedTableInfo(this, tableInfo);

        linkedTableInfo.loadFromXML(this, metaData, _namedFilters, errors);

        return linkedTableInfo;
    }

    /** Build up LabKey SQL that targets the desired container and appends any WHERE clauses (but not URL-style filters) */
    private QueryDefinition createQueryDef(String name, TableInfo sourceTable, @Nullable TableType xmlTable)
    {
        QueryDefinition queryDef = QueryServiceImpl.get().createQueryDef(_sourceSchema.getUser(), _sourceSchema.getContainer(), _sourceSchema, name);
        StringBuilder sql = new StringBuilder("SELECT\n");
        String sep = "";
        for (ColumnInfo col : sourceTable.getColumns())
        {
            sql.append(sep);
            sql.append("\"").append(col.getName()).append("\"");
            if (col.isHidden())
                sql.append(" @hidden");

            sep = ", ";
        }
        sql.append(" FROM \"");
        sql.append(_sourceSchema.getContainer().getPath());
        sql.append("\".");
        sql.append(_sourceSchema.getSchemaPath().toSQLString());
        sql.append(".\"");
        sql.append(sourceTable.getName());
        sql.append("\"\n");

        if (xmlTable != null && xmlTable.isSetFilters())
        {
            String separator = " WHERE ";

            LocalOrRefFiltersType filters = xmlTable.getFilters();
            if (filters.isSetRef())
            {
                // Add the WHERE from the referenced, shared filter
                for (NamedFiltersType namedFiltersType : _namedFilters)
                {
                    if (namedFiltersType.getName().equals(filters.getRef()))
                    {
                        for (String whereClause : namedFiltersType.getWhereArray())
                        {
                            sql.append(separator);
                            separator = " AND ";
                            sql.append("(");
                            sql.append(whereClause);
                            sql.append(")");
                        }
                    }
                }
            }

            // Add the filters that are specific to this table
            for (String whereClause : filters.getWhereArray())
            {
                sql.append(separator);
                separator = " AND ";
                sql.append("(");
                sql.append(whereClause);
                sql.append(")");
            }
        }

        queryDef.setSql(sql.toString());
        return queryDef;
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
            // For the linked schema's source container, grant ReaderRoler via the LimitedUser implementation
            if (policy.getContainerId().equals(_sourceContainer.getId()))
            {
                return super.getContextualRoles(policy);
            }

            // For all other containers, just rely on normal permissions
            return Collections.emptySet();
        }
    }
}
