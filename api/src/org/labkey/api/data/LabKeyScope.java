/*
 * Copyright (c) 2010 LabKey Corporation
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
import org.labkey.api.resource.AbstractResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Path;
import org.labkey.data.xml.TablesDocument;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: adam
 * Date: Aug 18, 2010
 * Time: 12:13:28 AM
 */
public class LabKeyScope extends DbScope
{
    private static final Logger LOG = Logger.getLogger(LabKeyScope.class);
    private static final AtomicLong _schemaLoadTime = new AtomicLong(0);

    public LabKeyScope(String dsName, DataSource dataSource) throws SQLException, ServletException
    {
        super(dsName, dataSource);
    }

    @Override
    // LabKey data source case.  Load meta data from database and overlay schema.xml.
    protected DbSchema loadSchema(String schemaName) throws Exception
    {
        // Load from database meta data
// TODO:        DbSchema schema  = super.loadSchema(schemaName);
        long startLoad = System.currentTimeMillis();

        LOG.info("Loading DbSchema \"" + this.getDisplayName() + "." + schemaName + "\"");

        DbSchema schema = DbSchema.createFromMetaData(schemaName, DbScope.getLabkeyScope());

        if (null != schema)
        {
            Resource resource = DbSchema.getSchemaResource(schemaName);

            if (resource == null)
                resource = new DbSchemaResource(schema);

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

            // Pre-emptively load all table meta data  TODO: Load them on demand
            schema.forceLoadAllTables();

            long elapsed = System.currentTimeMillis() - startLoad;

            _schemaLoadTime.addAndGet(elapsed);

            // Seems impossible, but we've seen a negative elapsed time (clock change in the middle of a schema load?).  See #11739.
            if (elapsed >= 0)
                LOG.info("" + schema.getTables().size() + " tables loaded in " + DateUtil.formatDuration(elapsed) + "; total: " + DateUtil.formatDuration(_schemaLoadTime.longValue()));
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
