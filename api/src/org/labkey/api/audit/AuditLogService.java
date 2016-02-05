/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: Karl Lum
 * Date: Sep 19, 2007
 */
public class AuditLogService
{
    private static final I _defaultProvider = new DefaultAuditProvider();
    private static final Map<String, AuditTypeProvider> _auditTypeProviders = new ConcurrentHashMap<>();

    private static I _instance;

    public static void registerAuditType(AuditTypeProvider provider)
    {
        if (!_auditTypeProviders.containsKey(provider.getEventName().toLowerCase()))
        {
            _auditTypeProviders.put(provider.getEventName().toLowerCase(), provider);
        }
        else
            throw new IllegalArgumentException("AuditTypeProvider '" + provider.getEventName() + "' is already registered");
    }

    public static List<AuditTypeProvider> getAuditProviders()
    {
        List<AuditTypeProvider> providers = new ArrayList<>(_auditTypeProviders.values());

        Collections.sort(providers, (o1, o2) -> (o1.getLabel().compareToIgnoreCase(o2.getLabel())));
        return Collections.unmodifiableList(providers);
    }

    public static AuditTypeProvider getAuditProvider(String eventType)
    {
        if (eventType == null)
            return null;
        return _auditTypeProviders.get(eventType.toLowerCase());
    }

    static public synchronized I get()
    {
        return _instance != null ? _instance : _defaultProvider;
    }

    static public synchronized void registerProvider(I provider)
    {
        // only one provider for now
        if (_instance != null && !(_instance instanceof AuditLogService.Replaceable))
            throw new IllegalStateException("An audit log provider :" + _instance.getClass().getName() + " has already been registered");

        _instance = provider;
        ServiceRegistry.get().registerService(AuditLogService.I.class, provider);
    }

    @Nullable
    static public UserSchema getAuditLogSchema(User user, Container container)
    {
        return QueryService.get().getUserSchema(user, container, AbstractAuditTypeProvider.QUERY_SCHEMA_NAME);
    }

    public interface I
    {
        /**
         * Specifies whether the provider produces displayable views.
         */
        boolean isViewable();

        <K extends AuditTypeEvent> K addEvent(User user, K event);

        @Nullable
        <K extends AuditTypeEvent> K getAuditEvent(User user, String eventType, int rowId);
        <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort);

        String getTableName();
        TableInfo getTable(ViewContext context, String name);
        UserSchema createSchema(User user, Container container);

        void registerAuditType(AuditTypeProvider provider);
        List<AuditTypeProvider> getAuditProviders();
        AuditTypeProvider getAuditProvider(String eventType);

        ActionURL getAuditUrl();
    }

    public interface Replaceable{}
}
