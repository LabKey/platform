/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.query.view;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.module.SimpleModuleUserSchema;
import org.labkey.api.security.User;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.data.DbUserSchemaTable;
import org.labkey.query.persist.ExternalSchemaDef;

import java.util.HashMap;
import java.util.Map;

public class ExternalSchema extends SimpleModuleUserSchema
{
    private Logger _log = Logger.getLogger(ExternalSchema.class);
    private ExternalSchemaDef _def;
    static final Map<String, DbSchema> s_schemaMap = new HashMap<String, DbSchema>();

    public ExternalSchema(User user, Container container, ExternalSchemaDef def)
    {
        super(def.getUserSchemaName(), "Contains data tables from the '" + def.getUserSchemaName() + "' database schema.", user, container, getDbSchema(def));
        _def = def;
        if (_dbSchema == null)
        {
            _dbSchema = CoreSchema.getInstance().getSchema();             // TODO: Assuming core schema is troubling... assert _dbSchema != null instead?
        }
    }

    public static DbSchema getDbSchema(ExternalSchemaDef def)
    {
        String dbSchemaName = def.getDbSchemaName();
        DbSchema dbSchema;

        synchronized (s_schemaMap)
        {
            if (s_schemaMap.containsKey(dbSchemaName))
            {
                dbSchema = s_schemaMap.get(dbSchemaName);
            }
            else
            {
                try
                {
                    DbScope scope = DbScope.getDbScope(def.getDataSource());
                    dbSchema = DbSchema.createFromMetaData(dbSchemaName, scope);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }

                s_schemaMap.put(dbSchemaName, dbSchema);
            }
        }

        return dbSchema;
    }

    public static void uncache(ExternalSchemaDef def)
    {
        s_schemaMap.remove(def.getDbSchemaName());
        DbScope scope = DbScope.getDbScope(def.getDataSource());
        scope.invalidateSchema(def.getDbSchemaName());
    }

    protected TableInfo createTable(String name, @NotNull SchemaTableInfo schematable)
    {
        DbUserSchemaTable ret = new DbUserSchemaTable(this, schematable, getXbTable(name));
        ret.setContainer(_def.getContainerId());
        return ret;
    }

    private TableType getXbTable(String name)
    {
        if (_def.getMetaData() == null)
            return null;
        try
        {
            TablesDocument doc = TablesDocument.Factory.parse(_def.getMetaData());
            if (doc.getTables() == null)
                return null;
            for (TableType tt : doc.getTables().getTableArray())
            {
                if (name.equalsIgnoreCase(tt.getTableName()))
                    return tt;
            }
            return null;
        }
        catch (XmlException e)
        {
            return null;
        }
    }

    public boolean areTablesEditable()
    {
        return _def.isEditable();
    }
}
