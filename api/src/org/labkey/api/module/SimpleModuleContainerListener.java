/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.module;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 3/4/13
 * Time: 10:45 AM
 */
public class SimpleModuleContainerListener extends ContainerManager.AbstractContainerListener
{
    private Module _owner;

    public SimpleModuleContainerListener(Module owner)
    {
        _owner = owner;
    }

    public void containerDeleted(Container c, User user)
    {
        for (final String schemaName : _owner.getSchemaNames())
        {
            purgeSchema(schemaName, c, user);
        }
    }

    protected void purgeSchema(String schemaName, Container c, User user)
    {
        // Module may be disabled so we need to create the UserSchema directly instead of using QueryService.get().getUserSchema().
        DbSchema dbSchema = DbSchema.get(schemaName);

        UserSchema userSchema = QueryService.get().createSimpleUserSchema(schemaName, null, user, c, dbSchema);
        if (userSchema == null)
            return;

        Logger.getLogger(SimpleModuleContainerListener.class).debug("Purging schema '" + schemaName + "' in container '" + c.getPath() + "'...");

        // Walk over the dbSchema's tables -- it's faster than walking the UserSchema's tables
        List<TableInfo> sorted = dbSchema.getSortedTables();
        Collections.reverse(sorted);
        for (TableInfo dbTable : sorted)
        {
            ColumnInfo containerCol = null;
            for (ColumnInfo column : dbTable.getColumns())
            {
                if ("container".equalsIgnoreCase(column.getName()))
                {
                    containerCol = column;
                    break;
                }
            }

            if (containerCol != null)
            {
                purgeTable(userSchema, dbTable, c, user);
            }
        }
    }

    protected void purgeTable(UserSchema userSchema, TableInfo dbTable, Container c, User u)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        if (dbTable.getTableType() == DatabaseTableType.TABLE)
        {
            Table.delete(dbTable, filter);
        }

        // Get the UserSchema TableInfo for the DbSchema's SchemaTableInfo based upon the table's name.
        TableInfo userTable = userSchema.getTable(dbTable.getName(), false);
        if (userTable != null)
        {
            Domain domain = userTable.getDomain();
            if (domain != null)
            {
                SQLFragment objectIds = domain.getDomainKind().sqlObjectIdsInDomain(domain);

                Integer[] ids = new SqlSelector(userTable.getSchema(), objectIds).getArray(Integer.class);
                OntologyManager.deleteOntologyObjects(c, true, ArrayUtils.toPrimitive(ids));
            }
        }
    }
}
