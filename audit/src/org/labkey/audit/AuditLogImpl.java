/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.audit.model.LogManager;
import org.labkey.audit.query.AuditQuerySchema;

import jakarta.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * User: Karl Lum
 * Date: Oct 4, 2007
 */
public class AuditLogImpl implements AuditLogService, StartupListener
{
    private static final AuditLogImpl _instance = new AuditLogImpl();

    private static final Logger _log = LogHelper.getLogger(AuditLogImpl.class, "Audit service interactions.");

    private final Queue<Pair<User, AuditTypeEvent>> _eventTypeQueue = new LinkedList<>();
    private final AtomicBoolean  _logToDatabase = new AtomicBoolean(false);
    private static final Object STARTUP_LOCK = new Object();

    // Cache the audit events associated with transaction ids. We currently use these for interacting with objects
    // that were created immediately after they were created, so the cache size does not need to be very large and the defaultTimeToLive can be small.
    // Use a pair as the cache object to avoid warnings about mutable cache objects (Issue 48779).
    // Since this is all about capturing data from the same transaction, there shouldn't be other threads in the mix.
    private static final Cache<Long, Pair<Long, List<AuditTypeEvent>>> TRANSACTION_EVENT_CACHE = CacheManager.getBlockingCache(50, CacheManager.HOUR,
            "Transaction Audit Event Cache",
            (key, argument) -> Pair.of(key, new ArrayList<>())
    );

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

    @Override
    public void moduleStartupComplete(ServletContext servletContext)
    {
        synchronized (STARTUP_LOCK)
        {
            // perform final domain initialization
            for (AuditTypeProvider provider : AuditLogService.get().getAuditProviders())
            {
                if (provider instanceof AbstractAuditTypeProvider auditTypeProvider)
                {
                    //      Domain domain = auditTypeProvider.getDomain();
                    // fixup any domain changes between the provisioned table and the domain kind
                    auditTypeProvider.ensureProperties();
                }
            }
            _logToDatabase.set(true);

            while (!_eventTypeQueue.isEmpty())
            {
                Pair<User, AuditTypeEvent> event = _eventTypeQueue.remove();
                addEvents(event.first, List.of(event.second));
            }
        }
    }

    @Override
    public boolean isViewable()
    {
        return true;
    }

    @Override
    public <K extends AuditTypeEvent> K addEvent(User user, K event)
    {
        return _addEvents(user, List.of(event),true, false);
    }

    @Override
    public <K extends AuditTypeEvent> void addEvents(@Nullable User user, List<K> events)
    {
        _addEvents(user, events, false, false);
    }

    @Override
    public <K extends AuditTypeEvent> void addEvents(@Nullable User user, List<K> events, boolean useTransactionAuditCache)
    {
        _addEvents(user, events, false, useTransactionAuditCache);
    }

    private <K extends AuditTypeEvent> K _addEvents(@Nullable User user, List<K> events, boolean reselectEvent, boolean useTransactionAuditCache)
    {
        assert !reselectEvent || events.size() == 1;

        for (var event : events)
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
            if (event.getTransactionId() != null && useTransactionAuditCache)
            {
                List<AuditTypeEvent> transactionEvents = TRANSACTION_EVENT_CACHE.get(event.getTransactionId()).second;
                transactionEvents.add(event);
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
        }

        try (var ignored = SpringActionController.ignoreSqlUpdates())
        {
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
                        for (var event : events)
                            LogManager.get().insertEvent(user, event);
                    }
                    else
                    {
                        for (var event : events)
                            _eventTypeQueue.add(new Pair<>(user, event));
                    }
                }
            }
            else
            {
                if (reselectEvent && events.size()==1)
                    return LogManager.get().insertEvent(user, events.get(0));
                LogManager.get().insertEvents(user, events);
            }
        }
        catch (RuntimeException e)
        {
            _log.error("Failed to insert audit log event", e);
            AuditLogService.handleAuditFailure(user, e);
            throw e;
        }
        return null;
    }

    @Override
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

    @Nullable
    @Override
    public <K extends AuditTypeEvent> K getAuditEvent(User user, String eventType, int rowId, @Nullable ContainerFilter cf)
    {
        return LogManager.get().getAuditEvent(user, eventType, rowId, cf);
    }

    @Override
    public <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        return LogManager.get().getAuditEvents(container, user, eventType, filter, sort);
    }

    @Override
    public <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort, @Nullable ContainerFilter cf)
    {
        return LogManager.get().getAuditEvents(container, user, eventType, filter, sort, cf);
    }

    @Override
    public ActionURL getAuditUrl()
    {
        return new ActionURL(AuditController.ShowAuditLogAction.class, ContainerManager.getRoot());
    }

    public List<Integer> getTransactionSampleIds(long transactionAuditId, User user, Container container)
    {
        List<AuditTypeEvent> transactionEvents = TRANSACTION_EVENT_CACHE.get(transactionAuditId).second;
        if (!transactionEvents.isEmpty())
        {
            List<Integer> ids = new ArrayList<>();
            transactionEvents.forEach(event -> {
                if (event instanceof SampleTimelineAuditEvent stEvent)
                    ids.add(stEvent.getSampleId());
            });
            return ids;
        }

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("TransactionID"), transactionAuditId);

        List<SampleTimelineAuditEvent> events = AuditLogService.get().getAuditEvents(container, user, SampleTimelineAuditEvent.EVENT_TYPE, filter, null);
        return events.stream().map(SampleTimelineAuditEvent::getSampleId).collect(Collectors.toList());
    }

    public List<Integer> getTransactionSourceIds(long transactionAuditId, User user, Container container)
    {
        List<String> lsids = new ArrayList<>();
        List<Integer> sourceIds = new ArrayList<>();
        List<AuditTypeEvent> transactionEvents = TRANSACTION_EVENT_CACHE.get(transactionAuditId).second;
        if (!transactionEvents.isEmpty())
        {
            transactionEvents.forEach(event -> {
                if (event instanceof DetailedAuditTypeEvent detailedEvent)
                {
                    if (detailedEvent.getNewRecordMap() != null)
                    {
                        Map<String, String> newRecord = AbstractAuditTypeProvider.decodeFromDataMap(detailedEvent.getNewRecordMap());
                        if (newRecord.containsKey("RowId"))
                            sourceIds.add(Integer.valueOf(newRecord.get("RowId")));
                        else if (newRecord.containsKey("LSID"))
                            lsids.add(newRecord.get("LSID"));
                    }
                }
            });
        }
        else
        {
            List<DetailedAuditTypeEvent> events = QueryService.get().getQueryUpdateAuditRecords(user, container, transactionAuditId);

            events.forEach((event) -> {
                if (event.getNewRecordMap() != null)
                {
                    Map<String, String> newRecord = AbstractAuditTypeProvider.decodeFromDataMap(event.getNewRecordMap());
                    if (newRecord.containsKey("RowId"))
                        sourceIds.add(Integer.valueOf(newRecord.get("RowId")));
                    else if (newRecord.containsKey("LSID"))
                        lsids.add(newRecord.get("LSID"));

                }
            });
        }
        if (!lsids.isEmpty())
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("LSID"), lsids, CompareType.IN);
            TableSelector selector = new TableSelector(ExperimentService.get().getTinfoData(), Collections.singleton("RowId"), filter, null);
            sourceIds.addAll(selector.getArrayList(Integer.class));
        }
        return sourceIds;
    }
}
