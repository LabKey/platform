/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PropertyQueryChangeListener implements QueryChangeListener
{
    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
    }

    private int updateLookupQuery(String newValue, SchemaKey schema, String oldQuery, Container container)
    {
        SQLFragment where = new SQLFragment("lookupschema = ? AND lookupquery = ? AND ")
                .append("(lookupcontainer = ? OR (lookupcontainer IS NULL AND container = ?))")
                .add(schema.toString())
                .add(oldQuery)
                .add(container)
                .add(container);

        return updateLookups("lookupquery", newValue, where);
    }

    private int updateLookupSchema(String newValue, String oldSchema, Container container)
    {
        SQLFragment where = new SQLFragment("lookupschema = ? AND ")
                .append("(lookupcontainer = ? OR (lookupcontainer IS NULL AND container = ?))")
                .add(oldSchema)
                .add(container)
                .add(container);

        return updateLookups("lookupschema", newValue, where);
    }

    private int updateLookups(String fieldName, String newValue, SQLFragment where)
    {
        TableInfo pdTable = OntologyManager.getTinfoPropertyDescriptor();
        List<String> propertyURIs = new SqlSelector(pdTable.getSchema(), new SQLFragment("SELECT PropertyURI FROM ").append(pdTable).append(" WHERE ").append(where)).getArrayList(String.class);
        if (propertyURIs.isEmpty())
            return 0;

        SQLFragment updateSql = new SQLFragment("UPDATE ").append(pdTable)
                .append(" SET ").append(fieldName).append(" = ? WHERE ")
                .add(newValue)
                .append(where);

        int updated = new SqlExecutor(pdTable.getSchema()).execute(updateSql);
        OntologyManager.uncachePropertyDescriptors(propertyURIs);
        return updated;
    }

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange<?>> changes)
    {
        if (!property.equals(QueryProperty.SchemaName) && !property.equals(QueryProperty.Name)) // Issue 53846
            return;

        // is there any other schema change other than assay renaming?
        boolean isSchemaChange = schema.toString().toLowerCase().startsWith("assay.general.");

        Map<String, String> queryNameChangeMap = new CaseInsensitiveHashMap<>();
        for (QueryPropertyChange<?> qpc : changes)
        {
            String oldVal = qpc.getOldValue() != null ? qpc.getOldValue().toString() : null;
            String newVal = qpc.getNewValue() != null ? qpc.getNewValue().toString() : null;
            if (oldVal != null && !oldVal.equals(newVal))
                queryNameChangeMap.put(oldVal, newVal);
        }

        int updated = 0;
        for (String oldValue : queryNameChangeMap.keySet())
        {
            String newValue = queryNameChangeMap.get(oldValue);
            if (isSchemaChange)
                updated += updateLookupSchema(newValue, oldValue, container);
            else
                updated += updateLookupQuery(newValue, schema, oldValue, container);
        }

        // GH Issue 1512: the direct SQL updates bypass OntologyManager, so its cached property descriptors still hold the old lookup target
        if (updated > 0)
            OntologyManager.clearCaches();
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {

    }

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        return List.of();
    }
}
