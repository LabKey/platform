/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheTimeChooser;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.AbstractResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.data.xml.TablesDocument;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: adam
 * Date: Aug 18, 2010
 * Time: 12:13:28 AM
 */
public class LabKeyScope extends DbScope
{
    private static final Logger LOG = Logger.getLogger(LabKeyScope.class);
    private static final Set<String> _moduleSchemaNames;

    static
    {
        _moduleSchemaNames = new LinkedHashSet<String>();

        for (Module module : ModuleLoader.getInstance().getModules())
            for (String schemaName : module.getSchemaNames())
                _moduleSchemaNames.add(schemaName);
    }

    public LabKeyScope(String dsName, DataSource dataSource) throws SQLException, ServletException
    {
        super(dsName, dataSource);
    }

    @Override
    public boolean isModuleSchema(String name)
    {
        return _moduleSchemaNames.contains(name);
    }

    // Much longer default value... although external schemas that live in the LabKey database will set a shorter
    // duration via the custom CacheTimeChooser be
    protected long getCacheDefaultTimeToLive()
    {
        return CacheManager.YEAR;
    }

    @Override
    protected CacheTimeChooser<String> getTableCacheTimeChooser()
    {
        return new CacheTimeChooser<String>()
        {
            @Override
            public Long getTimeToLive(String key, Object argument)
            {
                @SuppressWarnings({"unchecked"})
                DbSchema schema = ((Pair<DbSchema, String>)argument).first;

                return schema.isModuleSchema() ? null : CacheManager.HOUR;
            }
        };
    }

    @Override
    protected CacheTimeChooser<String> getSchemaCacheTimeChooser()
    {
        return new CacheTimeChooser<String>()
        {
            @Override
            public Long getTimeToLive(String key, Object argument)
            {
                // Module schemas are cached forever; external schemas in the LabKey database are cached for an hour.
                return _moduleSchemaNames.contains(key) ? null : CacheManager.HOUR;
            }
        };
    }

    @NotNull
    @Override
    // LabKey data source case.  Load meta data from database, load schema.xml, and stash it for later use.
    protected DbSchema loadSchema(String schemaName) throws Exception
    {
        LOG.info("Loading DbSchema \"" + getDisplayName() + "." + schemaName + "\"");

        // Load from database meta data
        DbSchema schema = super.loadSchema(schemaName);

        // Use the canonical schema name, not the requested name (which could differ in casing)
        Resource resource = DbSchema.getSchemaResource(schema.getName());

        if (null == resource)
        {
            String lowerName = schemaName.toLowerCase();

            if (!lowerName.equals(schema.getName()))
                resource = DbSchema.getSchemaResource(lowerName);

            if (null == resource)
            {
                LOG.warn("no schema metadata xml found for schema '" + schemaName + "'");
                resource = new DbSchemaResource(schema);
            }
        }

        schema.setResource(resource);

        InputStream xmlStream = null;

        try
        {
            xmlStream = resource.getInputStream();

            if (null != xmlStream)
            {
                TablesDocument tablesDoc = TablesDocument.Factory.parse(xmlStream);
                schema.setTablesDocument(tablesDoc);
            }
        }
        finally
        {
            try
            {
                if (null != xmlStream) xmlStream.close();
            }
            catch (Exception x)
            {
                LOG.error("LabKeyScope", x);
            }
        }

        return schema;
    }


    private static class DbSchemaResource extends AbstractResource
    {
        protected DbSchemaResource(DbSchema schema)
        {
            // CONSIDER: create a ResourceResolver based on DbScope
            super(new Path(schema.getName()), null);
        }

        @Override
        public Resource parent()
        {
            return null;
        }

        @Override
        public boolean exists()
        {
            // UNDONE: The DbSchemaResource could check if the schema exists
            // in the source database.  For now the DbSchemaResource always exists.
            return true;
        }

        @Override
        public long getVersionStamp()
        {
            // UNDONE: The DbSchemaResource could check if the schema is modified
            // in the source database.  For now the DbSchemaResource is always up to date.
            return 0L;
        }
    }
}
