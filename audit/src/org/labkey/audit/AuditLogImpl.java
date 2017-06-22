/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.audit.model.LogManager;
import org.labkey.audit.query.AuditQuerySchema;

import javax.servlet.ServletContext;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: Karl Lum
 * Date: Oct 4, 2007
 */
public class AuditLogImpl implements AuditLogService, StartupListener
{
    private static final AuditLogImpl _instance = new AuditLogImpl();

    private static final Logger _log = Logger.getLogger(AuditLogImpl.class);

    private Queue<Pair<User, AuditTypeEvent>> _eventTypeQueue = new LinkedList<>();
    private AtomicBoolean  _logToDatabase = new AtomicBoolean(false);
    private static final Object STARTUP_LOCK = new Object();

    public static AuditLogImpl get()
    {
        return _instance;
    }

    private AuditLogImpl()
    {
        ContextListener.addStartupListener(this);
    }

    @Override
    public String getName()
    {
        return "Audit Log";
    }

    public void moduleStartupComplete(ServletContext servletContext)
    {
        synchronized (STARTUP_LOCK)
        {
            _logToDatabase.set(true);

            while (!_eventTypeQueue.isEmpty())
            {
                Pair<User, AuditTypeEvent> event = _eventTypeQueue.remove();
                _addEvent(event.first, event.second);
            }
        }
    }

    public boolean isViewable()
    {
        return true;
    }

    public <K extends AuditTypeEvent> K addEvent(User user, K type)
    {
        return _addEvent(user, type);
    }

    private <K extends AuditTypeEvent> K _addEvent(User user, K event)
    {
        try
        {
            assert event.getContainer() != null : "Container cannot be null";

            if (event.getContainer() == null)
            {
                _log.warn("container was not specified for event type " + event.getEventType() + "; defaulting to root container.");
                Container root = ContainerManager.getRoot();
                event.setContainer(root.getId());
            }

            if (user == null)
            {
                if (HttpView.hasCurrentView() && HttpView.currentContext() != null)
                    _log.warn("user was not specified for event type " + event.getEventType() + " in container " + ContainerManager.getForId(event.getContainer()).getPath() + "; defaulting to guest user.");
                user = UserManager.getGuestUser();
            }

            // ensure some standard fields
            if (event.getCreated() == null)
                event.setCreated(new Date());
            if (event.getCreatedBy() == null)
                event.setCreatedBy(user);

            Container c = ContainerManager.getForId(event.getContainer());
            if (event.getProjectId() == null && c != null && c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            if (event.getImpersonatedBy() == null && user.isImpersonated())
            {
                User impersonatingUser = user.getImpersonatingUser();
                event.setImpersonatedBy(impersonatingUser.getUserId());
            }

            if (!_logToDatabase.get())
            {
                /*
                  This is necessary because audit log service needs to be registered in the constructor
                  of the audit module, but the schema may not be created or updated at that point.  Events
                  that occur before startup is complete are therefore queued up and recorded after startup.
                 */
                synchronized (STARTUP_LOCK)
                {
                    if (_logToDatabase.get())
                    {
                        LogManager.get()._insertEvent(user, event);
                    }
                    else
                        _eventTypeQueue.add(new Pair<>(user, (AuditTypeEvent)event));
                }
            }
            else
            {
                return LogManager.get()._insertEvent(user, event);
            }
        }
        catch (Exception e)
        {
            _log.error("Failed to insert audit log event", e);
            AuditLogService.handleAuditFailure(user, e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public String getTableName()
    {
        return AuditQuerySchema.AUDIT_TABLE_NAME;
    }

    public TableInfo getTable(ViewContext context, String name)
    {
        UserSchema schema = createSchema(context.getUser(), context.getContainer());
        return schema.getTable(name);
    }

    public UserSchema createSchema(User user, Container container)
    {
        return new AuditQuerySchema(user, container);
    }

    @Nullable
    @Override
    public <K extends AuditTypeEvent> K getAuditEvent(User user, String eventType, int rowId)
    {
        return LogManager.get().getAuditEvent(user, eventType, rowId);
    }

    @Override
    public <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        return LogManager.get().getAuditEvents(container, user, eventType, filter, sort);
    }

    @Override
    public ActionURL getAuditUrl()
    {
        return new ActionURL(AuditController.ShowAuditLogAction.class, ContainerManager.getRoot());
    }
}
