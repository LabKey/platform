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

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.data.ExternalSchemaTable;
import org.labkey.query.data.SimpleUserSchema;
import org.labkey.query.persist.ExternalSchemaDef;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExternalSchema extends SimpleUserSchema
{
    private final ExternalSchemaDef _def;
    private final Map<String, TableType> _metaDataMap;

    public ExternalSchema(User user, Container container, ExternalSchemaDef def)
    {
        super(def.getUserSchemaName(), "Contains data tables from the '" + def.getUserSchemaName() + "' database schema.",
                user, container, getDbSchema(def), getAvailableTables(def), getHiddenTables(def));

        _def = def;
        _metaDataMap = new CaseInsensitiveHashMap<TableType>();

        for (TableType tt : getTableTypes(def))
            _metaDataMap.put(tt.getTableName(), tt);
    }

    public static DbSchema getDbSchema(ExternalSchemaDef def)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());

        if (null != scope)
            return scope.getSchema(def.getDbSchemaName());
        else
            return null;
    }

    private static @NotNull Collection<String> getAvailableTables(ExternalSchemaDef def)
    {
        DbSchema schema = getDbSchema(def);

        if (null == schema)
            return Collections.emptySet();

        String allowedTables = def.getTables();

        if ("*".equals(allowedTables))
        {
            return schema.getTableNames();
        }
        else
        {
            return new CsvSet(allowedTables);
        }
    }

    private static @NotNull Collection<String> getHiddenTables(ExternalSchemaDef def)
    {
        Set<String> hidden = new HashSet<String>();

        for (TableType tt : getTableTypes(def))
            if (tt.getHidden())
                hidden.add(tt.getTableName());

        return hidden;
    }

    public static void uncache(ExternalSchemaDef def)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());

        if (null != scope)
        {
            String schemaName = def.getDbSchemaName();

            // Don't uncache the built-in LabKey schemas, even those pointed at by external schemas.  Reloading
            // these schemas is unnecessary (they don't change) and causes us to leak DbCaches.  See #10508.
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
        return _metaDataMap.get(name);
    }

    private static @NotNull TableType[] getTableTypes(ExternalSchemaDef def)
    {
        if (def.getMetaData() != null)
        {
            try
            {
                TablesDocument doc = TablesDocument.Factory.parse(def.getMetaData());

                if (doc.getTables() != null)
                    return doc.getTables().getTableArray();
            }
            catch (XmlException e)
            {
                // TODO: Throw or log or display this exception?
            }
        }

        return new TableType[0];
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
