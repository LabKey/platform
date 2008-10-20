/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.query.persist.DbUserSchemaDef;
import org.labkey.query.data.DbUserSchemaTable;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TableType;
import org.apache.xmlbeans.XmlException;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public class DbUserSchema extends UserSchema
{
    private Logger _log = Logger.getLogger(DbUserSchema.class);
    private Map<String, SchemaTableInfo> _tables = new CaseInsensitiveHashMap<SchemaTableInfo>();
    private DbUserSchemaDef _def;
    static final Map<String, DbSchema> s_schemaMap = new HashMap<String, DbSchema>();

    public DbUserSchema(User user, Container container, DbUserSchemaDef def)
    {
        super(def.getUserSchemaName(), user, container, null);
        _def = def;
        synchronized (s_schemaMap)
        {
            if (s_schemaMap.containsKey(def.getDbSchemaName()))
            {
                _dbSchema = s_schemaMap.get(def.getDbSchemaName());
            }
            else
            {
                try
                {
                    _dbSchema = DbSchema.createFromMetaData(def.getDbSchemaName());
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
                s_schemaMap.put(def.getDbSchemaName(), _dbSchema);
            }
        }

        if (_dbSchema != null)
        {
            SchemaTableInfo[] tables = _dbSchema.getTables();
            for (SchemaTableInfo table : tables)
            {
                _tables.put(table.getName(), table);
            }
        }
        
        if (_dbSchema == null)
        {
            _dbSchema = CoreSchema.getInstance().getSchema();
        }
    }

    public static void uncache(DbUserSchemaDef def)
    {
        s_schemaMap.remove(def.getDbSchemaName());
        DbSchema.invalidateSchema(def.getDbSchemaName());
    }

    public Set<String> getTableNames()
    {
        return _tables.keySet();
    }

    public TableInfo getTable(String name, String alias)
    {
        SchemaTableInfo baseTable = _tables.get(name);
        if (baseTable == null)
        {
            return super.getTable(name, alias);
        }
        DbUserSchemaTable ret = new DbUserSchemaTable(this, baseTable, getXbTable(name));
        ret.setContainer(_def.getDbContainer());
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

    public QueryView createView(ViewContext context, QuerySettings settings) throws ServletException
    {
        return new DbUserSchemaView(this, settings);
    }

    public boolean areTablesEditable()
    {
        return _def.isEditable();
    }

    public Container getDbContainer()
    {
        return null == _def.getDbContainer() ? null : ContainerManager.getForId(_def.getDbContainer());
    }
}
