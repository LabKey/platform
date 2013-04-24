package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: kevink
 * Date: 4/17/13
 */
public abstract class AbstractQueryChangeListener implements QueryChangeListener
{
    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        for (String query : queries)
            queryCreated(user, container, scope, schema, query);
    }

    protected abstract void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, String query);

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, QueryProperty property, Collection<QueryPropertyChange> changes)
    {
        for (QueryPropertyChange change : changes)
            queryChanged(user, container, scope, schema, change);
    }

    protected abstract void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, QueryPropertyChange change);

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        for (String query : queries)
            queryDeleted(user, container, scope, schema, query);
    }

    protected abstract void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, String query);

    @Override
    public Collection<String> queryDependents(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        List<String> ret = new ArrayList<>();
        for (String query : queries)
            ret.addAll(queryDependents(container, scope, schema, query));
        return ret;
    }

    protected abstract Collection<String> queryDependents(Container container, ContainerFilter scope, SchemaKey schema, String query);
}
