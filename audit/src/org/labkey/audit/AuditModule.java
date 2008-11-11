/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
    public static final String NAME = "Audit";
    private static final Logger _log = Logger.getLogger(AuditModule.class);
    private static Runnable _startupTask;

    protected Collection<? extends WebPartFactory> createWebPartFactories()
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
        return 8.30;
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

    public void startup(ModuleContext moduleContext)
    {
        AuditQuerySchema.register();
        AuditLogService.get().addAuditViewFactory(new SiteSettingsAuditViewFactory());
        AuditController.registerAdminConsoleLinks();

        if (_startupTask != null)
            _startupTask.run();
    }

    public Set<String> getSchemaNames()
    {
        return Collections.singleton("audit");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(AuditSchema.getInstance().getSchema());
    }

    public void afterSchemaUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.getInstalledVersion() < 8.24)
        {
            _startupTask = new Runnable() {
                public void run()
                {
                    convertAuditDataMaps();
                }
            };
        }
    }

    /**
     * Audit used to use PageFlowUtil.encodeObject to store audit detail information, this was bad for a number of reasons. Need to convert
     * those records to the newer, uncompressed format. Fortunately, there were only two event types at the time that used this encoding.
     */
    private void convertAuditDataMaps()
    {
        boolean startedTransaction = false;
        DbSchema schema = AuditSchema.getInstance().getSchema();

        try {
            if (!schema.getScope().isTransactionActive())
            {
                schema.getScope().beginTransaction();
                startedTransaction = true;
            }
            Container objectContainer = ContainerManager.getSharedContainer();
            SimpleFilter filter = new SimpleFilter();
            filter.addWhereClause("(Lsid IS NOT NULL) AND (EventType = ? OR EventType = ?)", new Object[]{"ListAuditEvent", "DatasetAuditEvent"});

            for (AuditLogEvent event : LogManager.get().getEvents(filter))
            {
                Map<String, ObjectProperty> dataMap = OntologyManager.getPropertyObjects(objectContainer.getId(), event.getLsid());
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

            if (startedTransaction)
                schema.getScope().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (startedTransaction && schema.getScope().isTransactionActive())
                schema.getScope().rollbackTransaction();
        }
    }
}