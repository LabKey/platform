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

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewChangeListener;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
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
    // issue 17760 - looking for expected fieldKey parents in the rename case, todo: where to put this list to keep it updated
    private static final List<String> EXPECTED_PARENT_FKS = Arrays.asList("DataSets", "DataSet", "ParticipantVisit");

    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
    }

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange> changes)
    {
        if (property.equals(QueryProperty.Name))
        {
            _updateCustomViewQueryNameChange(user, container, schema, changes);
        }
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        HttpServletRequest request = new MockHttpServletRequest();
        QueryServiceImpl svc = QueryServiceImpl.get();
        String schemaName = schema.toString();

        for (String query : queries)
        {
            // We do not want to get the inherited custom views because the child should not delete inherited views
            List<CustomView> views = svc.getCustomViews(user, container, null, schemaName, query, false);
            for (CustomView view : views)
            {
                if (view.isDeletable() && view.canEdit(container, null))
                    view.delete(user, request);
            }
        }
    }

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        QueryServiceImpl svc = QueryServiceImpl.get();
        String schemaName = schema.toString();

        List<String> ret = new ArrayList<>();
        List<CustomView> views = svc.getCustomViews(user, container, null, schemaName, null, true);
        VIEW_LOOP:
        for (CustomView view : views)
        {
            if (queries.contains(view.getQueryName()))
            {
                ret.add(dependentViewMessage(container, view));
                continue VIEW_LOOP;
            }

            for (FieldKey col : view.getColumns())
            {
                FieldKey colTable = col.getTable();
                if (colTable != null && colTable.getName() != null && queries.contains(colTable.getName()))
                {
                    ret.add(dependentViewMessage(container, view));
                    continue VIEW_LOOP;
                }
            }

            CustomViewInfo.FilterAndSort fas;
            try
            {
                fas = CustomViewInfo.FilterAndSort.fromString(view.getFilterAndSort());
            }
            catch (URISyntaxException e)
            {
                LogManager.getLogger(CustomViewQueryChangeListener.class).error("An error occurred finding custom view dependents: ", e);
                continue VIEW_LOOP;
            }

            for (FieldKey fieldKey : fas.getFieldKeys())
            {
                FieldKey tableFk = fieldKey.getTable();
                if (tableFk != null && queries.contains(tableFk.getName()))
                {
                    ret.add(dependentViewMessage(container, view));
                    continue VIEW_LOOP;
                }
            }
        }

        return ret;
    }

    private String dependentViewMessage(Container container, CustomView view)
    {
        String viewName = view.getName() == null ? "<default>" : view.getName();

        StringBuilder sb = new StringBuilder();
        sb.append("Custom view '").append(viewName).append("'");
        if (view.getContainer() != null && view.getContainer() != container)
            sb.append(" in container '").append(view.getContainer().getPath()).append("'");

        return sb.toString();
    }

    private void _updateCustomViewQueryNameChange(User user, Container container, SchemaKey schemaKey, Collection<QueryPropertyChange> changes)
    {
        // most property updates only care about the query name old value string and new value string
        Map<String, String> queryNameChangeMap = new HashMap<>();
        for (QueryPropertyChange qpc : changes)
        {
            queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
        }

        List<CustomView> databaseCustomViews = QueryService.get().getDatabaseCustomViews(user, container, null, schemaKey.toString(), null, false, false);

        for (CustomView customView : databaseCustomViews)
        {
            try
            {
                boolean hasUpdates = false;

                // update queryName (stored in query.CustomView)
                String queryName = customView.getQueryName();
                if (queryName != null && queryNameChangeMap.containsKey(queryName))
                {
                    customView.setQueryName(queryNameChangeMap.get(queryName));
                    hasUpdates = true;
                }

                // update custom view column list based on fieldKey parts
                boolean hasFieldKeyChanged = CustomViewChangeListener.updateCustomViewFieldKeyChange(customView, queryNameChangeMap, null);

                if (hasUpdates || hasFieldKeyChanged)
                {
                    HttpServletRequest request = new MockHttpServletRequest();
                    customView.save(customView.getModifiedBy(), request);
                }
            }
            catch (Exception e)
            {
                LogManager.getLogger(CustomViewQueryChangeListener.class).error("An error occurred upgrading custom view properties: ", e);
            }
        }
    }

}
