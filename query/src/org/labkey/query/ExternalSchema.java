/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExternalSchemaCustomizer;
import org.labkey.api.data.UserSchemaCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.data.xml.queryCustomView.NamedFiltersType;
import org.labkey.query.data.ExternalSchemaTable;
import org.labkey.query.persist.AbstractExternalSchemaDef;
import org.labkey.query.persist.ExternalSchemaDef;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExternalSchema extends SimpleUserSchema
{
    public static void register()
    {
        DefaultSchema.registerProvider(new DefaultSchema.DynamicSchemaProvider() {
            @Override
            public QuerySchema getSchema(User user, Container container, String name)
            {
                QueryServiceImpl svc = (QueryServiceImpl)QueryService.get();
                return svc.getExternalSchema(user, container, name);
            }

            @NotNull
            @Override
            public Collection<String> getSchemaNames(User user, Container container)
            {
                QueryServiceImpl svc = (QueryServiceImpl) QueryService.get();
                return svc.getExternalSchemas(user, container).keySet();
            }
        });
    }

    // Adaptor for DbSchema and UserSchema.
    protected interface TableSource
    {
        public Collection<String> getTableNames();
        public Collection<String> getQueryNames();
        public boolean isTableAvailable(String tableName);
    }

    protected final AbstractExternalSchemaDef _def;
    protected final TemplateSchemaType _template;
    protected final Map<String, TableType> _metaDataMap;
    protected final Map<String, NamedFiltersType> _namedFilters;

    public static ExternalSchema get(User user, Container container, ExternalSchemaDef def)
    {
        TemplateSchemaType template = def.lookupTemplate(container);
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

        final DbSchema schema = getDbSchema(def, template);
        if (null == schema)
            return null;
        TableSource tableSource = new TableSource()
        {
            public Collection<String> getTableNames()         { return schema.getTableNames(); }
            public Collection<String> getQueryNames()         { return Collections.emptyList(); }
            public boolean isTableAvailable(String tableName) { return schema.getTable(tableName) != null; }
        };

        Map<String, TableType> metaDataMap = getMetaDataMap(tableTypes);
        Collection<String> availableTables = getAvailableTables(def, template, tableSource, metaDataMap);
        Collection<String> hiddenTables = getHiddenTables(tableTypes);

        return new ExternalSchema(user, container, def, template, schema, metaDataMap, namedFilters, schemaCustomizers, availableTables, hiddenTables);
    }


    protected ExternalSchema(User user, Container container, AbstractExternalSchemaDef def, TemplateSchemaType template, DbSchema schema,
                             Map<String, TableType> metaDataMap,
                             NamedFiltersType[] namedFilters,
                             Collection<UserSchemaCustomizer> schemaCustomizers,
                             Collection<String> availableTables, Collection<String> hiddenTables)
    {
        super(def.getUserSchemaName(), "Contains data tables from the '" + def.getUserSchemaName() + "' database schema.",
                user, container, schema, schemaCustomizers, availableTables, hiddenTables);

        _def = def;
        _template = template;
        _metaDataMap = metaDataMap;

        // Create the SimpleFilters and give customizers a change to augment them.
        Map<String, NamedFiltersType> filters = new HashMap<>();
        if (namedFilters != null)
        {
            for (NamedFiltersType namedFilter : namedFilters)
                filters.put(namedFilter.getName(), namedFilter);
        }
        _namedFilters = fireCustomizeNamedFilters(filters);
    }

    private static DbSchema getDbSchema(ExternalSchemaDef def, TemplateSchemaType template)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());
        if (scope == null)
            return null;

        String sourceSchemaName = def.getSourceSchemaName();
        if (sourceSchemaName == null && template != null)
            sourceSchemaName = template.getSourceSchemaName();
        DbSchemaType type = def.isFastCacheRefresh() ? DbSchemaType.Fast : DbSchemaType.Bare;
        return scope.getSchema(sourceSchemaName, type);
    }

    @Nullable
    protected static TablesType parseTablesType(AbstractExternalSchemaDef def, TemplateSchemaType template)
    {
        String metadata = def.getMetaData();
        if (metadata != null)
        {
            try
            {
                TablesDocument doc = TablesDocument.Factory.parse(metadata);

                if (doc.getTables() != null)
                    return doc.getTables();
            }
            catch (XmlException e)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("Ignoring invalid schema metadata xml for '").append(def.getUserSchemaName()).append("'");
                String containerPath = def.getContainerPath();
                if (containerPath != null && !"".equals(containerPath))
                    sb.append(" in container '").append(containerPath).append("'");
                Logger.getLogger(ExternalSchema.class).warn(sb, e);
                return null;
            }
        }

        if (template != null && template.getMetadata() != null)
            return template.getMetadata().getTables();

        return null;
    }

    protected static @NotNull Collection<String> getAvailableTables(AbstractExternalSchemaDef def, TemplateSchemaType template, @Nullable TableSource tableSource, Map<String, TableType> metaDataMap)
    {
        if (null == tableSource)
            return Collections.emptySet();

        Collection<String> tableNames = getAvailableTables(def.getTables(), template, tableSource.getTableNames());

        if (tableNames.isEmpty() || metaDataMap.isEmpty())
            return tableNames;

        // Translate database names to XML table names
        List<String> xmlTableNames = new LinkedList<>();

        for (String tableName : tableNames)
        {
            TableType tt = metaDataMap.get(tableName);
            xmlTableNames.add(null == tt ? tableName : tt.getTableName());
        }

        return xmlTableNames;
    }

    protected static @NotNull Collection<String> getAvailableQueries(AbstractExternalSchemaDef def, TemplateSchemaType template, @Nullable TableSource tableSource)
    {
        if (null == tableSource)
            return Collections.emptySet();

        return getAvailableTables(def.getTables(), template, tableSource.getQueryNames());
    }

    private static @NotNull Collection<String> getAvailableTables(String savedTableNames, TemplateSchemaType template, Collection<String> sourceTableNames)
    {
        Set<String> requested = null;

        if (savedTableNames != null)
        {
            if ("*".equals(savedTableNames))
                return sourceTableNames;
            else
                requested = new CsvSet(savedTableNames);
        }
        else if (template != null)
        {
            TemplateSchemaType.Tables tables = template.getTables();
            if (tables != null)
            {
                String[] tableNames = tables.getTableNameArray();
                if (tableNames != null)
                {
                    if (tableNames.length == 1 && tableNames[0].equals("*"))
                        return sourceTableNames;
                    else
                        requested = new HashSet<>(Arrays.asList(tableNames));
                }
            }
        }

        if (requested == null || requested.isEmpty())
            return Collections.emptySet();

        // Some tables in the "requested" set may no longer exist or may be query names, so check each table in the schema, #13002
        // Also, we want to expose the current JDBC meta names, not the saved names, so map them. #19440
        Map<String, String> sourceNameMap = new CaseInsensitiveHashMap<>(sourceTableNames.size());
        for (String sourceTableName : sourceTableNames)
            sourceNameMap.put(sourceTableName, sourceTableName);

        Set<String> available = new HashSet<>(requested.size());
        for (String requestedName : requested)
        {
            String sourceTableName = sourceNameMap.get(requestedName);
            if (null != sourceTableName)
                available.add(sourceTableName);
        }

        return available;
    }

    protected static @NotNull Collection<String> getHiddenTables(TableType[] tableTypes)
    {
        Set<String> hidden = new HashSet<>();

        if (tableTypes != null)
        {
            for (TableType tt : tableTypes)
                if (tt.getHidden())
                    hidden.add(tt.getTableName());
        }

        return hidden;
    }

    protected static @NotNull Map<String, TableType> getMetaDataMap(TableType[] tableTypes)
    {
        Map<String, TableType> metaDataMap = new CaseInsensitiveHashMap<>();
        if (tableTypes != null)
        {
            for (TableType tt : tableTypes)
                metaDataMap.put(tt.getTableName(), tt);
        }
        return metaDataMap;
    }

    public static void uncache(ExternalSchemaDef def)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());

        if (null != scope)
        {
            // Uncache only the Bare schemas. Some (older) background is here #10508.
            scope.invalidateSchema(def.getSourceSchemaName(), DbSchemaType.Bare);
        }
    }

    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        ExternalSchemaTable ret = new ExternalSchemaTable(this, sourceTable, getXbTable(name));
        ret.init();
        ret.setContainer(_def.getContainerId());
        return ret;
    }

    protected TableType getXbTable(String name)
    {
        return _metaDataMap.get(name);
    }

    public boolean areTablesEditable()
    {
        return _def instanceof ExternalSchemaDef && _def.isEditable();
    }

    public boolean shouldIndexMetaData()
    {
        return _def instanceof ExternalSchemaDef && _def.isIndexable();
    }

    public boolean shouldFastCacheRefresh()
    {
        return _def instanceof ExternalSchemaDef && _def.isFastCacheRefresh();
    }

    @Override
    public boolean shouldRenderTableList()
    {
        // If more than 100 tables then don't try to render the list in the Query menu
        return getTableNames().size() <= 100;
    }

    protected Map<String, NamedFiltersType> fireCustomizeNamedFilters(Map<String, NamedFiltersType> filters)
    {
        if (_schemaCustomizers != null)
        {
            for (UserSchemaCustomizer customizer : _schemaCustomizers)
                if (customizer instanceof ExternalSchemaCustomizer)
                    ((ExternalSchemaCustomizer)customizer).customizeNamedFilters(filters);
        }
        return filters;
    }
}
