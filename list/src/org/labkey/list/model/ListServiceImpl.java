/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.list.model;

import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.list.list.ListController;
import org.apache.log4j.Logger;

import java.util.Map;
import java.sql.SQLException;

public class ListServiceImpl implements ListService.Interface
{
    static private final Logger _log = Logger.getLogger(ListServiceImpl.class);
    public Map<String, ListDefinition> getLists(Container container)
    {
        try
        {
            Map<String, ListDefinition> ret = new CaseInsensitiveHashMap<ListDefinition>();
            for (ListDef def : ListManager.get().getLists(container))
            {
                ListDefinition list = new ListDefinitionImpl(def);
                ret.put(list.getName(), list);
            }
            return ret;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
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

    public ListDefinition getList(Container container, int id)
    {
        try
        {
            ListDef def = ListManager.get().getList(container, id);
            return ListDefinitionImpl.of(def);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
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

}
