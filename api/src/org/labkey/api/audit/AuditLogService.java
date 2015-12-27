/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: Karl Lum
 * Date: Sep 19, 2007
 */
public class AuditLogService
{
    static private I _instance;
    static private final I _defaultProvider = new DefaultAuditProvider(); 
    private static Map<String, AuditTypeProvider> _auditTypeProviders = new ConcurrentHashMap<>();

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

        Collections.sort(providers, new Comparator<AuditTypeProvider>()
        {
            public int compare(AuditTypeProvider o1, AuditTypeProvider o2)
            {
                return (o1.getLabel().compareToIgnoreCase(o2.getLabel()));
            }
        });
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

        /**
         * Convenience methods to properly construct lsids with the correct audit namespace
         * @deprecated Only used for old AuditViewFactory Domains.
         */
        @Deprecated
        public String getDomainURI(String eventType);

        @Nullable
        public <K extends AuditTypeEvent> K getAuditEvent(User user, String eventType, int rowId);
        public <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort);

        public String getTableName();
        public TableInfo getTable(ViewContext context, String name);
        public UserSchema createSchema(User user, Container container);

        public void registerAuditType(AuditTypeProvider provider);
        public List<AuditTypeProvider> getAuditProviders();
        public AuditTypeProvider getAuditProvider(String eventType);

        public ActionURL getAuditUrl();
    }

    /**
     * @deprecated Replaced by {@link AuditTypeProvider}.
     */
    @Deprecated
    public interface AuditViewFactory
    {
        public String getEventType();
        public String getName();
        public String getDescription();
        
        public QueryView createDefaultQueryView(ViewContext context);
        public List<FieldKey> getDefaultVisibleColumns();
        public void setupTable(FilteredTable table, UserSchema schema);

        /**
         * Provides a way for factories to perform one time additional initialization (domain creation),
         * prior to logging an audit event.
         * @param context
         */
        public void initialize(ContainerUser context) throws Exception;
    }

    public interface Replaceable{}
}
