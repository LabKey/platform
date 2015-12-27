/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TestSchema;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.view.Portal;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 2:00:32 PM
 */
public class CoreContainerListener implements ContainerManager.ContainerListener
{
    private static final Logger _log = Logger.getLogger(CoreContainerListener.class);

    public void containerCreated(Container c, User user)
    {
        String message = c.getContainerNoun(true) + " " + c.getName() + " was created";
        addAuditEvent(user, c, message);
    }

    public void containerDeleted(Container c, User user)
    {
        try
        {
            PropertyManager.purgeObjectProperties(c);
            MvUtil.containerDeleted(c);

            // Delete any rows in test.TestTable associated with this container
            Table.delete(TestSchema.getInstance().getTableInfoTestTable(), SimpleFilter.createContainerFilter(c));

            // Let containerManager delete ACLs, we want that to happen last

            Portal.containerDeleted(c);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        String message = c.getName() + " was moved from " + oldParent.getPath() + " to " + c.getParent().getPath();
        addAuditEvent(user, c, message);
        // re-index is handled when the propertyChange() event fires
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        return Collections.emptyList();
    }

    private void addAuditEvent(User user, Container c, String comment)
    {
        if (user != null)
        {
            AuditTypeEvent event = new AuditTypeEvent(ContainerAuditProvider.CONTAINER_AUDIT_EVENT, c.getId(), comment);
            AuditLogService.get().addEvent(user, event);
        }
    }

    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
        ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent)propertyChangeEvent;
        ((CoreModule)ModuleLoader.getInstance().getCoreModule()).enumerateDocuments(null, evt.container, null);

        switch (evt.property)
        {
            case Name:
            {
                String oldValue = (String) evt.getOldValue();
                String newValue = (String) evt.getNewValue();
                String message = evt.container.getName() + " was renamed from " + oldValue + " to " + newValue;
                addAuditEvent(evt.user, evt.container, message);
                break;
            }
        }
    }
}
