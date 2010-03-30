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

package org.labkey.query.data;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.persist.ExternalSchemaDef;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DbUserSchemaCache
{
    private static final Logger _log = Logger.getLogger(DbUserSchemaCache.class);
    private static final DbUserSchemaCache instance = new DbUserSchemaCache();

    private final Map<Integer, SchemaEntry> map = new ConcurrentHashMap<Integer, SchemaEntry>();

    public static class DbUserSchemaException extends Exception
    {
        public DbUserSchemaException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

    private static class SchemaEntry
    {
        ExternalSchemaDef _externalSchemaDef;
        DbSchema schema;
    }

    public static DbUserSchemaCache get()
    {
        return instance;
    }

    public DbSchema getSchema(ExternalSchemaDef def)
    {
        Container container = ContainerManager.getForId(def.getContainerId());
        if (container == null)
            return null;


        SchemaEntry entry = map.get(def.getDbUserSchemaId());
        if (entry == null || !def.equals(entry._externalSchemaDef))
        {
            map.remove(def.getDbUserSchemaId());
            entry = makeSchemaEntry(def);
            map.put(def.getDbUserSchemaId(), entry);
        }
        return entry.schema;
    }

    public DbSchema loadSchema(ExternalSchemaDef externalSchemaDef) throws DbUserSchemaException
    {
        SchemaEntry ret = new SchemaEntry();
        ret._externalSchemaDef = externalSchemaDef;
        DbSchema schema;
        try
        {
            schema = DbSchema.createFromMetaData(externalSchemaDef.getDbSchemaName());
        }
        catch (Exception e)
        {
            throw new DbUserSchemaException("Error finding the schema '" + externalSchemaDef.getDbSchemaName() + "' in the database", e);
        }
        String metadata = externalSchemaDef.getMetaData();
        if (metadata != null)
        {

            TablesDocument tablesDoc;
            try
            {
                tablesDoc = TablesDocument.Factory.parse(metadata);
            }
            catch (XmlException e)
            {
                throw new DbUserSchemaException("Error parsing the metadata XML", e);
            }

            try
            {
                schema.loadXml(tablesDoc, true);
            }
            catch (Exception e)
            {
                throw new DbUserSchemaException("Error loading the metadata XML", e);
            }
        }
        return schema;
    }

    synchronized private SchemaEntry makeSchemaEntry(ExternalSchemaDef def)
    {
        SchemaEntry ret = new SchemaEntry();
        ret._externalSchemaDef = def;
        try
        {
            ret.schema = loadSchema(def);
        }
        catch (DbUserSchemaException dbuse)
        {
            // ignore
        }
        return ret;
    }

    public void remove(int dbUserSchemaId)
    {
        map.remove(dbUserSchemaId);
    }
}
