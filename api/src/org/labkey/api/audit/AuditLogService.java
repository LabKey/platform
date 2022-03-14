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

package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: Karl Lum
 * Date: Sep 19, 2007
 */
public interface AuditLogService
{
    AuditLogService _defaultProvider = new DefaultAuditProvider();
    Map<String, AuditTypeProvider> _auditTypeProviders = new ConcurrentHashMap<>();
    List<AuditFailureHandlerProvider> auditFailureHandlerProviders = new CopyOnWriteArrayList<>();

    static AuditLogService get()
    {
        AuditLogService svc = ServiceRegistry.get().getService(AuditLogService.class);
        return svc != null ? svc : _defaultProvider;
    }

    static void registerProvider(AuditLogService provider)
    {
        ServiceRegistry.get().registerService(AuditLogService.class, provider);
    }

    @Nullable
    static UserSchema getAuditLogSchema(User user, Container container)
    {
        return QueryService.get().getUserSchema(user, container, AbstractAuditTypeProvider.QUERY_SCHEMA_NAME);
    }

    default void registerAuditType(AuditTypeProvider provider)
    {
        assert ModuleLoader.getInstance().isStartupInProgress() : "Audit types must be registered in Module.doStartup()";

        if (!_auditTypeProviders.containsKey(provider.getEventName().toLowerCase()))
        {
            _auditTypeProviders.put(provider.getEventName().toLowerCase(), provider);
        }
        else
            throw new IllegalArgumentException("AuditTypeProvider '" + provider.getEventName() + "' is already registered");
    }

    default List<AuditTypeProvider> getAuditProviders()
    {
        List<AuditTypeProvider> providers = new ArrayList<>(_auditTypeProviders.values());

        providers.sort(Comparator.comparing(AuditTypeProvider::getLabel, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(providers);
    }

    default AuditTypeProvider getAuditProvider(String eventType)
    {
        if (eventType == null)
            return null;
        return _auditTypeProviders.get(eventType.toLowerCase());
    }

    /**
     * Specifies whether the provider produces displayable views.
     */
    boolean isViewable();

    <K extends AuditTypeEvent> K addEvent(User user, K event);

    <K extends AuditTypeEvent> void addEvents(User user, List<K> events);

    @Nullable
    <K extends AuditTypeEvent> K getAuditEvent(User user, String eventType, int rowId);

    <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort);

    <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort, @Nullable ContainerFilter cf);

    String getTableName();

    TableInfo getTable(ViewContext context, String name);

    UserSchema createSchema(User user, Container container);

    ActionURL getAuditUrl();

    interface AuditFailureHandlerProvider
    {
        void handleAuditFailure(User user, Throwable e);
    }

    static void addAuditFailureHandlerProvider(AuditFailureHandlerProvider provider)
    {
        auditFailureHandlerProviders.add(provider);
    }

    static List<AuditFailureHandlerProvider> getAuditFailureHandlerProviders()
    {
        return auditFailureHandlerProviders;
    }

    static void handleAuditFailure(User user, Throwable e)
    {
        DbScope scope = DbScope.getLabKeyScope();
        // See issue 36948 - can't do things like look up configurations for how to handle audit logging transactions
        // if there's a now-trashed connection associated with a transaction
        if (scope.isTransactionActive())
        {
            // Use a different transaction kind to ensure a different DB connection is used
            try (DbScope.Transaction t = scope.ensureTransaction(TRANSACTION_KIND))
            {
                for (AuditFailureHandlerProvider provider : getAuditFailureHandlerProviders())
                    provider.handleAuditFailure(user, e);

                t.commit();
            }
        }
        else
        {
            for (AuditFailureHandlerProvider provider : getAuditFailureHandlerProviders())
                provider.handleAuditFailure(user, e);
        }
    }

    DbScope.TransactionKind TRANSACTION_KIND = () -> "AuditLog";

    interface Replaceable{}
}
