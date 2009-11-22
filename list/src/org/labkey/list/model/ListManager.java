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

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;

import java.sql.SQLException;

public class ListManager
{
    static private ListManager instance;
    public static final String LIST_AUDIT_EVENT = "ListAuditEvent";

    synchronized static public ListManager get()
    {
        if (instance == null)
            instance = new ListManager();
        return instance;
    }

    public DbSchema getSchema()
    {
        return ExperimentService.get().getSchema();
    }

    public TableInfo getTinfoList()
    {
        return getSchema().getTable("list");
    }

    public TableInfo getTinfoIndexInteger()
    {
        return getSchema().getTable("indexInteger");
    }

    public TableInfo getTinfoIndexVarchar()
    {
        return getSchema().getTable("indexVarchar");
    }

    public ListDef[] getLists(Container container) throws SQLException
    {
        ListDef.Key key = new ListDef.Key(container);
        return key.select();
    }

    public ListDef[] getAllLists() throws SQLException
    {
        return Table.select(getTinfoList(), Table.ALL_COLUMNS, null, null, ListDef.class);
    }

    public ListDef getList(Container container, int id) throws SQLException
    {
        SimpleFilter filter = new PkFilter(getTinfoList(), id);
        filter.addCondition("Container", container);
        return Table.selectObject(getTinfoList(), filter, null, ListDef.class);
    }
    
    public ListDef insert(User user, ListDef def) throws SQLException
    {
        return Table.insert(user, getTinfoList(), def);
    }

    public ListDef update(User user, ListDef def) throws SQLException
    {
        return Table.update(user, getTinfoList(), def, def.getRowId());
    }
}
