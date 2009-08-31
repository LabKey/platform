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

package org.labkey.biotrue.query;

import org.labkey.api.query.*;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.security.User;
import org.labkey.biotrue.datamodel.BtManager;
import org.labkey.biotrue.controllers.BtController;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

public class BtSchema extends UserSchema
{
    static public final String name = "biotrue";
    public static final String DESCRIPTION = "Contains data about connections to BioTrue servers.";
    static public final DefaultSchema.SchemaProvider PROVIDER = new DefaultSchema.SchemaProvider()
    {
        public QuerySchema getSchema(DefaultSchema schema)
        {
            return new BtSchema(schema.getUser(), schema.getContainer());
        }
    };
    public BtSchema(User user, Container container)
    {
        super(name, DESCRIPTION, user, container, BtManager.get().getSchema());
    }

    public enum TableType
    {
        Servers,
        Entities,
        Tasks,
    }
    final static Set<String> tableNames = new LinkedHashSet<String>();
    static
    {
        for (TableType type : TableType.values())
        {
            tableNames.add(type.toString());
        }
    }

    public Set<String> getTableNames()
    {
        return tableNames;
    }

    public TableInfo createTable(String name)
    {
        if (tableNames.contains(name))
        {
            switch (TableType.valueOf(name))
            {
                case Tasks:
                    return createTasksTable();
                case Servers:
                    return createServersTable();
                case Entities:
                    return createEntitiesTable();
            }
        }
        return null;
    }

    public TableInfo createTasksTable()
    {
        FilteredTable ret = new FilteredTable(BtManager.get().getTinfoTask());
        SQLFragment containerCondition = new SQLFragment();
        containerCondition.append("(SELECT biotrue.server.container\n" +
                "FROM biotrue.server\n" +
                "WHERE biotrue.server.rowid = biotrue.task.serverid) = ?");
        containerCondition.add(_container.getId());
        ret.addCondition(containerCondition);
        ret.addWrapColumn(ret.getRealTable().getColumn("RowId")).setKeyField(true);
        ColumnInfo colEntity = ret.wrapColumn("Entity", ret.getRealTable().getColumn("EntityId"));
        colEntity.setFk(new QueryForeignKey(this, TableType.Entities.toString(), "RowId", "Name"));
        ret.addWrapColumn(ret.getRealTable().getColumn("Operation"));
        ret.addColumn(colEntity);
        ColumnInfo colServer = ret.wrapColumn("Server", ret.getRealTable().getColumn("ServerId"));
        colServer.setFk(new QueryForeignKey(this, TableType.Servers.toString(), "RowId", "Name"));
        ret.addColumn(colServer);
        ret.addWrapColumn(ret.getRealTable().getColumn("Started"));
        return ret;
    }

    public TableInfo createServersTable()
    {
        FilteredTable ret = new FilteredTable(BtManager.get().getTinfoServer(), _container);
        ret.addWrapColumn(ret.getRealTable().getColumn("RowId")).setHidden(true);
        ret.addWrapColumn(ret.getRealTable().getColumn("Name"));
        ret.setTitleColumn("Name");
        ret.addWrapColumn(ret.getRealTable().getColumn("UserName"));
        ret.addWrapColumn(ret.getRealTable().getColumn("WsdlURL"));
        ret.addWrapColumn(ret.getRealTable().getColumn("PhysicalRoot"));
        ret.setDetailsURL(new DetailsURL(BtController.Action.showServer.url(getContainer()), Collections.singletonMap("serverId", "RowId")));
        return ret;
    }

    public TableInfo createEntitiesTable()
    {
        FilteredTable ret = new FilteredTable(BtManager.get().getTinfoEntity());
        SQLFragment containerCondition = new SQLFragment("(SELECT biotrue.server.container FROM biotrue.server WHERE biotrue.server.rowid = biotrue.entity.serverid) = ?");
        containerCondition.add(getContainer().getId());
        ret.addCondition(containerCondition);
        ret.addWrapColumn(ret.getRealTable().getColumn("RowId")).setKeyField(true);
        ret.addWrapColumn(ret.getRealTable().getColumn("ServerId")).setFk(new QueryForeignKey(this, TableType.Servers.toString(), "RowId", "Name"));
        ret.addColumn(ret.wrapColumn("Name", ret.getRealTable().getColumn("BioTrue_Name")));
        ret.addWrapColumn(ret.getRealTable().getColumn("PhysicalName"));
        ColumnInfo parentColumn = ret.wrapColumn("Parent", ret.getRealTable().getColumn("ParentId"));
        parentColumn.setFk(new LookupForeignKey("Entities", "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return createEntitiesTable();
            }
        });
        ret.addColumn(parentColumn);
        ret.setTitleColumn("Name");
        return ret;
    }
}
