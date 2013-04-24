package org.labkey.query;

import common.Logger;
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
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, QueryProperty property, Collection<QueryPropertyChange> changes)
    {
        if (property != null && property.equals(QueryProperty.Name))
        {
            _updateQuerySnapshotQueryNameChange(user, container, schema, changes);
        }
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
    }

    @Override
    public Collection<String> queryDependents(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        // UNDONE
        return Collections.emptyList();
    }

    private void _updateQuerySnapshotQueryNameChange(User user, Container container, SchemaKey schemaKey, Collection<QueryPropertyChange> changes)
    {
        // most property updates only care about the query name old value string and new value string
        Map<String, String> queryNameChangeMap = new HashMap<String, String>();
        for (QueryPropertyChange qpc : changes)
        {
            queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
        }

        for (QuerySnapshotDefinition qsd : QueryService.get().getQuerySnapshotDefs(container, schemaKey.toString()))
        {
            try
            {
                // update QueryTableName (stored in query.QuerySnapshotDef)
                String queryTableName = qsd.getQueryTableName();
                if (queryTableName != null && queryNameChangeMap.containsKey(queryTableName))
                {
                    qsd.setQueryTableName(queryNameChangeMap.get(queryTableName));
                    qsd.save(user);
                }
            }
            catch (Exception e)
            {
                Logger.getLogger(QuerySnapshotQueryChangeListener.class).error("An error occurred upgrading query snapshot properties: ", e);
            }
        }
    }
}
