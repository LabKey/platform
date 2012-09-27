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
    public String getDescription()
    {
        return "External schema reload";
    }

    @Override
    public String getName()
    {
        return "SchemaReload";
    }

    @Override
    public boolean canDisable()
    {
        return true;
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
