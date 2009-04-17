/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.QcUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 2:00:32 PM
 */
public class CoreContainerListener implements ContainerManager.ContainerListener
{
    private static Logger _log = Logger.getLogger(CoreContainerListener.class);

    public void containerCreated(Container c)
    {
        User user = UserManager.getGuestUser();
        try {
            ViewContext context = HttpView.currentContext();
            if (context != null)
            {
                user = context.getUser();
            }
        }
        catch (RuntimeException e){}
        String message = c.isProject() ? "Project " + c.getName() + " was created" :
                "Folder " + c.getName() + " was created";
        addAuditEvent(user, c, message);
    }

    public void containerDeleted(Container c, User user)
    {
        try
        {
            PropertyManager.purgeObjectProperties(c.getId());
            QcUtil.containerDeleted(c);
            // Let containerManager delete ACLs, we want that to happen last

            String message = c.isProject() ? "Project " + c.getName() + " was deleted" :
                    "Folder " + c.getName() + " was deleted";
            addAuditEvent(user, c, message);
        }
        catch (SQLException e)
        {
            _log.error("Failed to delete Properties for container '" + c.getPath() + "'.", e);
        }
    }


    private void addAuditEvent(User user, Container c, String comment)
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setEventType(ContainerManager.CONTAINER_AUDIT_EVENT);
            event.setContainerId(c.getId());
            event.setComment(comment);

            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            AuditLogService.get().addEvent(event);
        }
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
