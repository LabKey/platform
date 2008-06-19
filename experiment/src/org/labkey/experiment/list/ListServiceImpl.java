/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.list;

import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.experiment.controllers.list.ListController;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.sql.SQLException;

public class ListServiceImpl implements ListService.Interface
{
    static private final Logger _log = Logger.getLogger(ListServiceImpl.class);
    public Map<String, ListDefinition> getLists(Container container)
    {
        try
        {
            Map<String, ListDefinition> ret = new TreeMap<String, ListDefinition>();
            for (ListDef def : ListManager.get().getLists(container))
            {
                ListDefinition list = new ListDefinitionImpl(def);
                ret.put(list.getName(), list);
            }
            return ret;
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return Collections.emptyMap();
        }
    }

    public boolean hasLists(Container container)
    {
        try
        {
            ListDef[] lists = ListManager.get().getLists(container);
            return lists != null && lists.length > 0;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ListDefinition createList(Container container, String name)
    {
        return new ListDefinitionImpl(container, name);
    }

    public ListDefinition getList(int id)
    {
        ListDef def = ListManager.get().getList(id);
        return ListDefinitionImpl.of(def);
    }

    public ListDefinition getList(Domain domain)
    {
        try
        {
            ListDef.Key key = new ListDef.Key(domain.getContainer());
            key.addCondition(ListDef.Column.domainId, domain.getTypeId());
            return ListDefinitionImpl.of(key.selectObject());
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public ActionURL getManageListsURL(Container container)
    {
        return new ActionURL(ListController.BeginAction.class, container);
    }

    // These transaction methods were added to abstract the detail that
    // Lists use the ExperimentService for their database storage.
    public void beginTransaction() throws SQLException
    {
        ExperimentService.get().beginTransaction();
    }

    public void commitTransaction() throws SQLException
    {
        ExperimentService.get().commitTransaction();
    }

    public void rollbackTransaction()
    {
        ExperimentService.get().rollbackTransaction();
    }

    public boolean isTransactionActive()
    {
        return ExperimentService.get().isTransactionActive();
    }

    public String getSchemaName()
    {
        return ListSchema.NAME;
    }

    public QueryUpdateService getQueryUpdateService(String queryName, Container container, User user)
    {
        Map<String, ListDefinition> listDefs =  getLists(container);
        if(null == listDefs)
            throw new NotFoundException("No lists found in the container '" + container.getPath() + "'.");

        ListDefinition listDef = listDefs.get(queryName);
        if(null == listDef)
            throw new NotFoundException("List '" + queryName + "' was not found in the container '" + container.getPath() + "'.");

        return new ListQueryUpdateService(listDef);
    }
}
