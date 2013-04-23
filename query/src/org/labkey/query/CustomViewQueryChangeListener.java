package org.labkey.query;

import common.Logger;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.Sort;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if (property != null && property.equals(QueryProperty.Name))
        {
            _updateCustomViewQueryNameChange(container, schema, changes);
        }
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

        List<String> ret = new ArrayList<String>();
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

    private void _updateCustomViewQueryNameChange(Container container, SchemaKey schemaKey, Collection<QueryPropertyChange> changes)
    {
        // most property updates only care about the query name old value string and new value string
        Map<String, String> queryNameChangeMap = new HashMap<String, String>();
        for (QueryPropertyChange qpc : changes)
        {
            queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
        }

        // TODO: need to also get private custom views
        for (CustomView customView : QueryService.get().getCustomViews(null, container, schemaKey.toString(), null, false))
        {
            try {
                boolean hasUpdates = false;

                // update queryName (stored in query.CustomView)
                String queryName = customView.getQueryName();
                if (queryName != null && queryNameChangeMap.containsKey(queryName))
                {
                    customView.setQueryName(queryNameChangeMap.get(queryName));
                    hasUpdates = true;
                }

                // update custom view column list based on fieldKey table name
                boolean columnsUpdated = false;
                List<FieldKey> updatedColumns = new ArrayList<FieldKey>();
                for (FieldKey col : customView.getColumns())
                {
                    FieldKey colTable = col.getTable();
                    if (colTable != null && colTable.getName() != null && queryNameChangeMap.containsKey(colTable.getName()))
                    {
                        updatedColumns.add(new FieldKey(new FieldKey(colTable.getParent(), queryNameChangeMap.get(colTable.getName())), col.getName()));
                        columnsUpdated = true;
                    }
                    else
                    {
                        updatedColumns.add(col);
                    }
                }
                if (columnsUpdated)
                {
                    customView.setColumns(updatedColumns);
                    hasUpdates = true;
                }

                CustomViewInfo.FilterAndSort fas = CustomViewInfo.FilterAndSort.fromString(customView.getFilterAndSort());

                // update filter info list based on fieldKey table name
                boolean filtersUpdated = false;
                for (FilterInfo filterInfo : fas.getFilter())
                {
                    FieldKey filterTable = filterInfo.getField().getTable();
                    if (filterTable != null && filterTable.getName() != null && queryNameChangeMap.containsKey(filterTable.getName()))
                    {
                        filterInfo.setField(new FieldKey(new FieldKey(filterTable.getParent(), queryNameChangeMap.get(filterTable.getName())), filterInfo.getField().getName()));
                        filtersUpdated = true;
                    }
                }

                // update sort field list based on fieldKey table name
                boolean sortsUpdated = false;
                for (Sort.SortField sortField : fas.getSort())
                {
                    FieldKey sortTable = sortField.getFieldKey().getTable();
                    if (sortTable != null && sortTable.getName() != null && queryNameChangeMap.containsKey(sortTable.getName()))
                    {
                        sortField.setFieldKey(new FieldKey(new FieldKey(sortTable.getParent(), queryNameChangeMap.get(sortTable.getName())), sortField.getFieldKey().getName()));
                        sortsUpdated = true;
                    }
                }

                // update aggregates based on fieldKey table name
                boolean aggregatesUpdated = false;
                for (Aggregate aggregate : fas.getAggregates())
                {
                    FieldKey aggTable = aggregate.getFieldKey().getTable();
                    if (aggTable != null && aggTable.getName() != null && queryNameChangeMap.containsKey(aggTable.getName()))
                    {
                        aggregate.setFieldKey(new FieldKey(new FieldKey(aggTable.getParent(), queryNameChangeMap.get(aggTable.getName())), aggregate.getFieldKey().getName()));
                        aggregatesUpdated = true;
                    }
                }

                if (filtersUpdated || sortsUpdated || aggregatesUpdated)
                {
                    customView.setFilterAndSortFromURL(CustomViewInfo.FilterAndSort.toURL(fas), CustomViewInfo.FILTER_PARAM_PREFIX);
                    hasUpdates = true;
                }

                if (hasUpdates)
                {
                    HttpServletRequest request = new MockHttpServletRequest();
                    customView.save(User.getReferenceFixupUser(), request);
                }
            }
            catch (Exception e)
            {
                Logger.getLogger(CustomViewQueryChangeListener.class).error("An error occurred upgrading custom view properties: ", e);
            }
        }
    }
}
