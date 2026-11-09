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
package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;

import java.util.Collection;
import java.util.HashSet;

public class QueryDatasetQueryChangeListener implements QueryChangeListener
{
    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {

    }

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange<?>> changes)
    {
        if (property.equals(QueryProperty.Name))
        {
            for (QueryPropertyChange<?> change : changes)
            {
                String oldVal = (String)change.getOldValue();
                String newVal = (String)change.getNewValue();

                StudySchema ss = StudySchema.getInstance();

                TableInfo table = ss.getTableInfoDataset();
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SourceQueryName"), oldVal);
                filter.addCondition(FieldKey.fromParts("SourceQuerySchema"), schema.toString());
                filter.addCondition(FieldKey.fromParts("SourceQueryContainer"), container.getId());

                TableSelector ts = new TableSelector(table, filter, null);
                ts.getArrayList(DatasetDefinition.class).forEach(_def ->
                {
                    _def.setSourceQueryName(newVal);
                    _def.save(user);
                });
            }
        }
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        StudySchema ss = StudySchema.getInstance();
        TableInfo table = ss.getTableInfoDataset();

        for (String query : queries)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SourceQueryName"), query);
            filter.addCondition(FieldKey.fromParts("SourceQuerySchema"), schema.toString());
            filter.addCondition(FieldKey.fromParts("SourceQueryContainer"), container.getId());

            TableSelector ts = new TableSelector(table, filter, null);
            ts.getArrayList(DatasetDefinition.class).forEach(_def -> _def.delete(user));
        }
    }

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        StudySchema ss = StudySchema.getInstance();
        TableInfo table = ss.getTableInfoDataset();
        Collection<String> dependents = new HashSet<>();

        for (String query : queries)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SourceQueryName"), query);
            filter.addCondition(FieldKey.fromParts("SourceQuerySchema"), schema.toString());
            filter.addCondition(FieldKey.fromParts("SourceQueryContainer"), container.getId());

            TableSelector ts = new TableSelector(table, filter, null);
            ts.getArrayList(DatasetDefinition.class).forEach(_def -> dependents.add(_def.getName()));
        }

        return dependents;
    }
}
