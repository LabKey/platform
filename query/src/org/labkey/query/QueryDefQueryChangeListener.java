package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;

import java.util.Collection;
import java.util.List;

public class QueryDefQueryChangeListener implements QueryChangeListener
{

    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {}

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange> changes)
    {
        if (property.equals(QueryProperty.Name))
        {
            String oldName = (String)changes.iterator().next().getOldValue();
            String newName = (String)changes.iterator().next().getNewValue();
            QueryManager.get().renameQuery(user, container, schema.toString(), oldName, newName);
        }
        if (property.equals(QueryProperty.SchemaName))
        {
            String oldName = (String)changes.iterator().next().getOldValue();
            QueryManager.get().renameSchema(user, container, oldName, schema.toString());
        }
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        for (String query : queries)
        {
            QueryDef queryDef = QueryManager.get().getQueryDef(container, schema.toString(), query, false);
            if (queryDef != null)
                QueryManager.get().delete(queryDef);
        }
    }

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        return List.of();
    }
}
