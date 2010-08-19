package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.labkey.api.resource.AbstractResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Path;
import org.labkey.data.xml.TablesDocument;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Aug 18, 2010
 * Time: 12:13:28 AM
 */
public class LabKeyScope extends DbScope
{
    private static final Logger LOG = Logger.getLogger(LabKeyScope.class);

    public LabKeyScope(String dsName, DataSource dataSource) throws SQLException, ServletException
    {
        super(dsName, dataSource);
    }

    @Override
    // LabKey data source case.  Load schema.xml, reload schema if it's stale.
    protected DbSchema loadSchema(DbSchema schema, String schemaName) throws Exception
    {
        InputStream xmlStream = null;

        try
        {
            Resource resource;

            if (null == schema)
            {
                resource = DbSchema.getSchemaResource(schemaName);
            }
            else
            {
                if (AppProps.getInstance().isDevMode() && schema.isStale())
                {
                    resource = schema.getResource();
                }
                else
                {
                    return schema;
                }
            }

            // Force a reload from meta data
            schema = super.loadSchema(null, schemaName);

            if (null != schema)
            {
                if (resource == null)
                    resource = new DbSchemaResource(schema);

                schema.setResource(resource);
                xmlStream = resource.getInputStream();

                if (null != xmlStream)
                {
                    TablesDocument tablesDoc = TablesDocument.Factory.parse(xmlStream);
                    schema.loadXml(tablesDoc, true);
                }
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
