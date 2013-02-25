/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.audit;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.data.*;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.audit.model.LogManager;
import org.labkey.audit.query.AuditQuerySchema;

import java.sql.SQLException;
import java.util.*;

public class AuditModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(AuditModule.class);

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean hasScripts()
    {
        return true;
    }

    public String getName()
    {
        return "Audit";
    }

    public double getVersion()
    {
        return 12.30;
    }

    protected void init()
    {
        AuditLogService.registerProvider(AuditLogImpl.get());
        addController("audit", AuditController.class);
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    public void doStartup(ModuleContext moduleContext)
    {
        AuditQuerySchema.register();
        AuditLogService.get().addAuditViewFactory(new SiteSettingsAuditViewFactory());
        AuditController.registerAdminConsoleLinks();

        // This schema conversion can't use the normal UpgradeCode process because it requires Ontology service to be started
        if (!moduleContext.isNewInstall() && moduleContext.getOriginalVersion() < 8.24)
            convertAuditDataMaps();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(AuditSchema.SCHEMA_NAME);
    }

    /**
     * Audit used to use PageFlowUtil.encodeObject to store audit detail information, this was bad for a number of reasons. Need to convert
     * those records to the newer, uncompressed format. Fortunately, there were only two event types at the time that used this encoding.
     */
    private void convertAuditDataMaps()
    {
        DbSchema schema = AuditSchema.getInstance().getSchema();

        try {
            schema.getScope().ensureTransaction();

            Container objectContainer = ContainerManager.getSharedContainer();
            SimpleFilter filter = new SimpleFilter();
            filter.addWhereClause("(Lsid IS NOT NULL) AND (EventType = ? OR EventType = ?)", new Object[]{"ListAuditEvent", "DatasetAuditEvent"});

            for (AuditLogEvent event : LogManager.get().getEvents(filter, null))
            {
                Map<String, ObjectProperty> dataMap = OntologyManager.getPropertyObjects(objectContainer, event.getLsid());
                if (dataMap != null)
                {
                    Map<String, Object> newDataMap = new HashMap<String, Object>();

                    for (ObjectProperty prop : dataMap.values())
                    {
                        if (prop.value() == null)
                            continue;
                        Map<String, String> decodedMap = SimpleAuditViewFactory._safeDecodeFromDataMap(String.valueOf(prop.value()));
                        if (!decodedMap.isEmpty())
                        {
                            // re-encode the data map and replace the previous object property
                            OntologyManager.deleteProperty(event.getLsid(), prop.getPropertyURI(), objectContainer, objectContainer);
                            newDataMap.put(prop.getName(), SimpleAuditViewFactory.encodeForDataMap(decodedMap, true));
                        }
                    }
                    if (!newDataMap.isEmpty())
                        AuditLogImpl.addEventProperties(event.getLsid(), AuditLogService.get().getDomainURI(event.getEventType()), newDataMap);
                }
            }

            schema.getScope().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            schema.getScope().closeConnection();
        }
    }
}