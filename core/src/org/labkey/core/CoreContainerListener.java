/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
package org.labkey.core;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TestSchema;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.view.Portal;

import java.beans.PropertyChangeEvent;
import java.util.Objects;

public class CoreContainerListener implements ContainerManager.ContainerListener
{
    @Override
    public void containerCreated(Container c, User user)
    {
        containerCreated(c, user, null);
    }

    @Override
    public void containerCreated(Container c, User user, @Nullable String auditMsg)
    {
        String message = auditMsg == null ? c.getContainerNoun(true) + " " + c.getName() + " was created" : auditMsg;
        addAuditEvent(user, c, message);
        ((CoreModule)ModuleLoader.getInstance().getCoreModule()).enumerateDocuments(SearchService.get().defaultTask().getQueue(c, SearchService.PRIORITY.modified), null);
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        PropertyManager.purgeObjectProperties(c);
        MvUtil.containerDeleted(c);

        // Delete any rows in test.TestTable associated with this container
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);
        Table.delete(TestSchema.getInstance().getTableInfoTestTable(), containerFilter);

        // Data States
        Table.delete(CoreSchema.getInstance().getTableInfoDataStates(), containerFilter);

        // report engine folder mapping
        Table.delete(CoreSchema.getInstance().getTableInfoReportEngineMap(), containerFilter);

        Portal.containerDeleted(c);

        // Note: ContainerManager deletes security policies and DB sequences after it deletes the container
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        String message = c.getName() + " was moved from " + oldParent.getPath() + " to " + c.getParent().getPath();
        addAuditEvent(user, c, message);
        // re-index is handled when the propertyChange() event fires
    }

    private void addAuditEvent(User user, Container c, String comment)
    {
        if (user != null)
        {
            AuditTypeEvent event = new AuditTypeEvent(ContainerAuditProvider.CONTAINER_AUDIT_EVENT, c, comment);
            AuditLogService.get().addEvent(user, event);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
        ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent)propertyChangeEvent;
        Container c = evt.container;
        ((CoreModule) ModuleLoader.getInstance().getCoreModule()).enumerateDocuments(SearchService.get().defaultTask().getQueue(c, SearchService.PRIORITY.modified), null);

        if (Objects.requireNonNull(evt.property) == ContainerManager.Property.Name)
        {
            String oldValue = (String) evt.getOldValue();
            String newValue = (String) evt.getNewValue();
            String message = c.getName() + " was renamed from " + oldValue + " to " + newValue;
            addAuditEvent(evt.user, c, message);
        }
    }
}
