package org.labkey.query.persist;

import org.apache.log4j.Logger;
import org.labkey.api.data.DbScope;
import org.labkey.api.util.Formats;
import org.labkey.api.util.SystemMaintenance;

import java.util.HashSet;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 22, 2010
 * Time: 9:34:51 PM
 */
public class SchemaReloadMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    private static final Logger LOG = Logger.getLogger(SchemaReloadMaintenanceTask.class);

    @Override
    public String getMaintenanceTaskName()
    {
        return "External schema reload";
    }

    @Override
    public void run()
    {
        ExternalSchemaDef[] defs = QueryManager.get().getExternalSchemaDefs(null);
        Set<String> reloaded = new HashSet<String>();

        for (ExternalSchemaDef def : defs)
        {
            // Avoid reloading the same schema
            String key = def.getDataSource() + "|" + def.getDbSchemaName();

            if (!reloaded.contains(key))
            {
                DbScope scope = DbScope.getDbScope(def.getDataSource());

                if (null != scope)
                {
                    String name = scope.getDisplayName() + "." + def.getDbSchemaName();

                    try
                    {
                        long start = System.currentTimeMillis();
                        QueryManager.get().reloadExternalSchema(def);
                        double duration = (double)(System.currentTimeMillis() - start) / 1000.0;
                        LOG.info("Reloaded " + name + " in " + Formats.f1.format(duration) + " seconds");
                    }
                    catch (Exception e)
                    {
                        LOG.info("Error reloading " + name + ": " + e.getMessage());
                    }
                }

                reloaded.add(key);
            }
        }
    }
}
