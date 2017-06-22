/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * QueryChangeListener for query snapshots.
 *
 * User: cnathe
 * Date: 4/19/13
 */
public class QuerySnapshotQueryChangeListener implements QueryChangeListener
{
    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
    }

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange> changes)
    {
        if (property.equals(QueryProperty.Name))
        {
            _updateQuerySnapshotQueryNameChange(user, container, schema, changes);
        }
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
    }

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        // UNDONE
        return Collections.emptyList();
    }

    private void _updateQuerySnapshotQueryNameChange(User user, Container container, SchemaKey schemaKey, Collection<QueryPropertyChange> changes)
    {
        // most property updates only care about the query name old value string and new value string
        Map<String, String> queryNameChangeMap = new HashMap<>();
        for (QueryPropertyChange qpc : changes)
        {
            queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
        }

        for (QuerySnapshotDefinition qsd : QueryService.get().getQuerySnapshotDefs(container, schemaKey.toString()))
        {
            try
            {
                // update QueryTableName (stored in query.QuerySnapshotDef)
                boolean changed = false;
                String queryTableName = qsd.getQueryTableName();
                if (null != queryTableName && queryNameChangeMap.containsKey(queryTableName))
                {
                    qsd.setQueryTableName(queryNameChangeMap.get(queryTableName));
                    changed = true;
                }
                String snapshotName = qsd.getName();
                if (null != snapshotName && queryNameChangeMap.containsKey(snapshotName))
                {
                    qsd.setName(queryNameChangeMap.get(snapshotName));
                    changed = true;
                }
                if (changed)
                    qsd.save(qsd.getModifiedBy());
            }
            catch (Exception e)
            {
                Logger.getLogger(QuerySnapshotQueryChangeListener.class).error("An error occurred upgrading query snapshot properties: ", e);
            }
        }
    }
}
