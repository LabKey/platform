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

package org.labkey.query;

import org.apache.commons.lang.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.Filter;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.data.ExternalSchemaTable;
import org.labkey.query.data.SimpleUserSchema;
import org.labkey.query.persist.ExternalSchemaDef;

import java.util.Arrays;
import java.util.Set;

public class ExternalSchema extends SimpleUserSchema
{
    private final ExternalSchemaDef _def;

    public ExternalSchema(User user, Container container, ExternalSchemaDef def)
    {
        super(def.getUserSchemaName(), "Contains data tables from the '" + def.getUserSchemaName() + "' database schema.", user, container, getDbSchema(def), new ExternalSchemaFilter(def));
        _def = def;
    }

    private static class ExternalSchemaFilter implements Filter<TableInfo>
    {
        private final Set<String> _tableNameSet;

        private ExternalSchemaFilter(ExternalSchemaDef def)
        {
            String allowedTables = def.getTables();

            if ("*".equals(allowedTables))
            {
                _tableNameSet = null;
            }
            else
            {
                _tableNameSet = new CaseInsensitiveHashSet(Arrays.asList(StringUtils.split(allowedTables, ",")));
            }
        }

        @Override
        public boolean accept(TableInfo table)
        {
            return null == _tableNameSet || _tableNameSet.contains(table.getName());
        }
    }

    public static DbSchema getDbSchema(ExternalSchemaDef def)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());

        if (null != scope)
            return scope.getSchema(def.getDbSchemaName());
        else
            return null;
    }

    public static void uncache(ExternalSchemaDef def)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());

        if (null != scope)
        {
            String schemaName = def.getDbSchemaName();

            // Don't uncache the built-in LabKey schemas, even those pointed at by external schemas.  Reloading
            // these schemas is unncessary (they don't change) and causes us to leak DbCaches.  See #10508.
            if (scope != DbScope.getLabkeyScope() || !DbSchema.getModuleSchemaNames().contains(schemaName))
                scope.invalidateSchema(def.getDbSchemaName());
        }
    }

    protected TableInfo createTable(String name, @NotNull SchemaTableInfo schematable)
    {
        ExternalSchemaTable ret = new ExternalSchemaTable(this, schematable, getXbTable(name));
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

    public boolean shouldIndexMetaData()
    {
        return _def.isIndexable();
    }
}
