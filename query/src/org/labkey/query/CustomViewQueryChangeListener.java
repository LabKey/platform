package org.labkey.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.SchemaKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * QueryChangeListener for custom views.
 *
 * User: kevink
 * Date: 4/18/13
 */
public class CustomViewQueryChangeListener implements QueryChangeListener
{
    @Override
    public void queryCreated(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
    }

    @Override
    public void queryChanged(Container container, ContainerFilter scope, SchemaKey schema, QueryProperty property, Collection<QueryPropertyChange> changes)
    {
    }

    @Override
    public void queryDeleted(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
    }

    @Override
    public Collection<String> queryDependents(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        QueryServiceImpl svc = QueryServiceImpl.get();
        String schemaName = schema.toString();

        List<String> ret = new ArrayList<>();
        for (String queryName : queries)
        {
            // UNDONE: Need to get all custom views -- null User only fetches shared custom views.
            List<CustomView> views = svc.getCustomViews(null, container, schemaName, queryName, true);
            for (CustomView view : views)
            {
                String viewName = view.getName() == null ? "<default>" : view.getName();

                if (view.getContainer() != null && view.getContainer() != container)
                    ret.add("Custom view '" + viewName + "' in container '" + view.getContainer().getPath() + "'");
                else
                    ret.add("Custom view '" + viewName + "'");
            }
        }
        return ret;
    }
}
