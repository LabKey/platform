/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.biotrue.datamodel;

import org.labkey.api.data.*;
import org.labkey.api.security.User;

import java.sql.SQLException;

public class BtManager
{
    static private BtManager instance = new BtManager();
    static public BtManager get()
    {
        return instance;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get("biotrue");
    }

    public TableInfo getTinfoServer()
    {
        return getSchema().getTable("Server");
    }

    public TableInfo getTinfoEntity()
    {
        return getSchema().getTable("Entity");
    }

    public TableInfo getTinfoSession()
    {
        return getSchema().getTable("Session");
    }

    public TableInfo getTinfoTask()
    {
        return getSchema().getTable("Task");
    }

    public Server[] getServers(Container container) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("container", container.getId());
        return Table.select(getTinfoServer(), Table.ALL_COLUMNS, filter, null, Server.class);
    }

    public Server getServer(int id)
    {
        return Table.selectObject(getTinfoServer(), id, Server.class);
    }

    public void deleteServer(int id) throws SQLException
    {
        // also delete all the entities associated with this server
        SimpleFilter filter = new SimpleFilter("ServerId", id);
        Table.delete(getTinfoEntity(), filter);
        Table.delete(getTinfoServer(), id);
    }
    
    public Server insertServer(User user, Server server) throws SQLException
    {
        return Table.insert(user, getTinfoServer(), server);
    }

    public Server updateServer(User user, Server server) throws SQLException
    {
        return Table.update(user, getTinfoServer(), server, server.getRowId());
    }

    public Entity[] getEntities(Server server, Entity parent) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("serverId", server.getRowId());
        if (parent != null)
        {
            filter.addCondition("parentId", parent.getRowId());
        }
        else
        {
            filter.addCondition("parentId", null, CompareType.ISBLANK);
        }
        return Table.select(getTinfoEntity(), Table.ALL_COLUMNS, filter, null, Entity.class);
    }

    public Entity getEntity(int id)
    {
        return Table.selectObject(getTinfoEntity(), id, Entity.class);
    }

    public Entity getEntity(Server server, Entity parent, String bioTrueId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ServerId", server.getRowId());
        if (parent == null)
        {
            filter.addCondition("ParentId", 0);
        }
        else
        {
            filter.addCondition("ParentId", parent.getRowId());
        }
        filter.addCondition("BioTrue_Id", bioTrueId);
        return new TableSelector(getTinfoEntity(), filter, null).getObject(Entity.class);
    }

    public Entity ensureEntity(Server server, Entity parent, String bioTrueId, String ent, String name) throws SQLException
    {
        Entity ret = getEntity(server, parent, bioTrueId);
        if (ret != null)
            return ret;
        Entity child = new Entity();
        child.setServerId(server.getRowId());
        if (parent != null)
        {
            child.setParentId(parent.getRowId());
        }
        child.setBioTrue_Id(bioTrueId);
        child.setBioTrue_Type(ent);
        child.setBioTrue_Name(name);
        return Table.insert(null, getTinfoEntity(), child);
    }

    public Entity updateEntity(Entity entity) throws SQLException
    {
        return Table.update(null, getTinfoEntity(), entity, entity.getRowId());
    }

    public Server insert(Server server) throws SQLException
    {
        return Table.insert(null, getTinfoServer(), server);
    }

    public Session insert(Session session) throws SQLException
    {
        return Table.insert(null, getTinfoSession(), session);
    }

    public Session update(Session session) throws SQLException
    {
        return Table.update(null, getTinfoSession(), session, session.getRowId());
    }

    public Session getSession(int rowid)
    {
        return Table.selectObject(getTinfoSession(), rowid, Session.class);
    }

    public boolean isDisabled()
    {
        return getTinfoServer() == null;
    }
}
