/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
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

    protected final AbstractExternalSchemaDef _def;
    protected final TemplateSchemaType _template;
    protected final Map<String, TableType> _metaDataMap;
    protected final NamedFiltersType[] _namedFilters;

    public static ExternalSchema get(User user, Container container, ExternalSchemaDef def)
    {
        TemplateSchemaType template = def.lookupTemplate(container);
        TablesType tablesType = parseTablesType(def, template);

        NamedFiltersType[] namedFilters = null;
        TableType[] tableTypes = null;
        if (tablesType != null)
        {
            namedFilters = tablesType.getFiltersArray();
            tableTypes = tablesType.getTableArray();
        }

        DbSchema schema = getDbSchema(def, template);

        Map<String, TableType> metaDataMap = getMetaDataMap(tableTypes);
        Collection<String> availableTables = getAvailableTables(def, template, schema, metaDataMap);
        Collection<String> hiddenTables = getHiddenTables(tableTypes);

        return new ExternalSchema(user, container, def, template, schema, metaDataMap, namedFilters, availableTables, hiddenTables);
    }

    protected ExternalSchema(User user, Container container, AbstractExternalSchemaDef def, TemplateSchemaType template, DbSchema schema,
                             Map<String, TableType> metaDataMap,
                             NamedFiltersType[] namedFilters,
                             Collection<String> availableTables, Collection<String> hiddenTables)
    {
        super(def.getUserSchemaName(), "Contains data tables from the '" + def.getUserSchemaName() + "' database schema.",
                user, container, schema, availableTables, hiddenTables);

        _def = def;
        _template = template;
        _metaDataMap = metaDataMap;
        _namedFilters = namedFilters;
    }

    private static DbSchema getDbSchema(ExternalSchemaDef def, TemplateSchemaType template)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());
        if (scope == null)
            return null;

        String sourceSchemaName = template != null ? template.getSourceSchemaName() : def.getSourceSchemaName();
        return scope.getSchema(sourceSchemaName);
    }

    @Nullable
    protected static TablesType parseTablesType(AbstractExternalSchemaDef def, TemplateSchemaType template)
    {
        if (template != null && template.getMetadata() != null)
            return template.getMetadata().getTables();

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
                // TODO: Throw or log or display this exception?
            }
        }

        return null;
    }

    private static @NotNull Collection<String> getAvailableTables(ExternalSchemaDef def, TemplateSchemaType template, @Nullable DbSchema schema, Map<String, TableType> metaDataMap)
    {
        Collection<String> tableNames = getAvailableTables(def, template, schema);

        if (tableNames.isEmpty() || metaDataMap.isEmpty())
            return tableNames;

        // Translate database names to XML table names
        List<String> xmlTableNames = new LinkedList<String>();

        for (String tableName : tableNames)
        {
            TableType tt = metaDataMap.get(tableName);
            xmlTableNames.add(null == tt ? tableName : tt.getTableName());
        }

        return xmlTableNames;
    }

    private static @NotNull Collection<String> getAvailableTables(ExternalSchemaDef def, TemplateSchemaType template, @Nullable DbSchema schema)
    {
        if (null == schema)
            return Collections.emptySet();

        Set<String> allowed = null;
        if (template != null)
        {
            TemplateSchemaType.Tables tables = template.getTables();
            if (tables != null)
            {
                String[] tableNames = tables.getTableNameArray();
                if (tableNames != null)
                {
                    if (tableNames.length == 1 && tableNames[0].equals("*"))
                        return schema.getTableNames();
                    else
                        allowed = new HashSet<String>(Arrays.asList(tableNames));
                }
            }
        }
        else
        {
            String allowedTableNames = def.getTables();
            if ("*".equals(allowedTableNames))
                return schema.getTableNames();
            else
                allowed = new CsvSet(allowedTableNames);
        }

        if (allowed == null || allowed.size() == 0)
            return Collections.emptySet();

        // Some tables in the "allowed" list may no longer exist, so check each table in the schema.  #13002
        Set<String> available = new HashSet<String>(allowed.size());
        for (String name : allowed)
            if (null != schema.getTable(name))
                available.add(name);

        return available;
    }

    private static @NotNull Collection<String> getHiddenTables(TableType[] tableTypes)
    {
        Set<String> hidden = new HashSet<String>();

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
        Map<String, TableType> metaDataMap = new CaseInsensitiveHashMap<TableType>();
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
            String schemaName = def.getSourceSchemaName();

            // Don't uncache module schemas, even those pointed at by external schemas.  Reloading these schemas is
            // unnecessary (they don't change) and causes us to leak DbCaches.  See #10508.
            if (!scope.isModuleSchema(schemaName))
                scope.invalidateSchema(def.getSourceSchemaName());
        }
    }

    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        ExternalSchemaTable ret = new ExternalSchemaTable(this, sourceTable, getXbTable(name));
        ret.setContainer(_def.getContainerId());
        return ret;
    }

    protected TableType getXbTable(String name)
    {
        return _metaDataMap.get(name);
    }

    public boolean areTablesEditable()
    {
        return _def instanceof ExternalSchemaDef && ((ExternalSchemaDef)_def).isEditable();
    }

    public boolean shouldIndexMetaData()
    {
        return _def instanceof ExternalSchemaDef && ((ExternalSchemaDef)_def).isIndexable();
    }

    @Override
    public boolean shouldRenderTableList()
    {
        // If more than 100 tables then don't try to render the list in the Query menu
        return getTableNames().size() <= 100;
    }
}
