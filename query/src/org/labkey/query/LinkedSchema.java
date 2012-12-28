/*
 * Copyright (c) 2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.query.persist.LinkedSchemaDef;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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

    public static LinkedSchema get(User user, Container container, LinkedSchemaDef def)
    {
        TemplateSchemaType template = def.lookupTemplate(container);
        UserSchema sourceSchema = getSourceSchema(def, template, user);
        if (sourceSchema == null)
            return null;

        Map<String, TableType> metaDataMap = new CaseInsensitiveHashMap<TableType>();

        return new LinkedSchema(user, container, def, template, sourceSchema, metaDataMap, sourceSchema.getTableNames(), Collections.<String>emptySet());
    }

    private static UserSchema getSourceSchema(LinkedSchemaDef def, TemplateSchemaType template, User user)
    {
        SchemaKey sourceSchemaName = SchemaKey.fromString(template != null ? template.getSourceSchemaName() : def.getSourceSchemaName());

        Container sourceContainer = def.lookupSourceContainer();
        if (sourceContainer == null || sourceSchemaName == null)
            return null;

        UserSchema sourceSchema = QueryService.get().getUserSchema(user, sourceContainer, sourceSchemaName);
        return sourceSchema;
    }

    private LinkedSchema(User user, Container container, LinkedSchemaDef def, TemplateSchemaType template, UserSchema sourceSchema,
                         Map<String, TableType> metaDataMap, Collection<String> availableTables, Collection<String> hiddenTables)
    {
        super(user, container, def, template, sourceSchema.getDbSchema(), metaDataMap, availableTables, hiddenTables);

        _sourceSchema = sourceSchema;
    }

    @Override
    public Map<String, QueryDefinition> getQueryDefs()
    {
        Map<String, QueryDefinition> queries = QueryService.get().getQueryDefs(getUser(), _sourceSchema.getContainer(), _sourceSchema.getSchemaName());
        Map<String, QueryDefinition> ret = new CaseInsensitiveHashMap<QueryDefinition>(queries.size());

        for (String key : queries.keySet())
        {
            QueryDefinition queryDef = queries.get(key);
            LinkedSchemaQueryDefinition wrappedQueryDef = new LinkedSchemaQueryDefinition(this, queryDef);
            ret.put(key, wrappedQueryDef);
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
        return new LinkedTableInfo(this, sourceTable);
    }

}
