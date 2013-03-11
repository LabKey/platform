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

package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Sep 19, 2007
 */
public class AuditLogService
{
    static private I _instance;
    static private final I _defaultProvider = new DefaultAuditProvider(); 
    private static Map<String, AuditViewFactory> _auditViewFactories = new ConcurrentHashMap<String, AuditViewFactory>();

    public static void addAuditViewFactory(AuditViewFactory factory)
    {
        AuditViewFactory previous = _auditViewFactories.put(factory.getEventType(), factory);

        if (null != previous)
            throw new IllegalStateException("AuditViewFactory \"" + factory.getEventType() + "\" is already registered: "
                    + previous.getClass().getName() + " vs. " + factory.getClass().getName());
    }

    public static AuditViewFactory getAuditViewFactory(String eventType)
    {
        return _auditViewFactories.get(eventType);
    }

    public static List<AuditViewFactory> getAuditViewFactories()
    {
        List<AuditViewFactory> factories = new ArrayList<AuditViewFactory>(_auditViewFactories.values());

        Collections.sort(factories, new Comparator<AuditViewFactory>()
        {
            public int compare(AuditViewFactory o1, AuditViewFactory o2)
            {
                return (o1.getName().compareToIgnoreCase(o2.getName()));
            }
        });
        return Collections.unmodifiableList(factories);
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

    public interface I
    {
        /**
         * Specifies whether the provider produces displayable views.
         */
        public boolean isViewable();

        @Deprecated // Use non-ViewContext version
        public AuditLogEvent addEvent(ViewContext context, String eventType, String key, String message);

        public AuditLogEvent addEvent(User user, Container c, String eventType, String key, String message);
        public AuditLogEvent addEvent(User user, Container c, String eventType, String key1, String key2, String message);
        public AuditLogEvent addEvent(User user, Container c, String eventType, int key, String message);
        public AuditLogEvent addEvent(AuditLogEvent event);

        /**
         * Adds the audit event, plus additional properties contained in the dataMap. The dataMap should map
         * property names to values, with the properties coming from the valid set of properties from the
         * specified domain. The domain and associated properties must have been created in advance, in order
         * for the query views to correctly display the additional properties, URI's for domains and properties
         * should be created using the methods on this service.
         */
        public AuditLogEvent addEvent(AuditLogEvent event, Map<String, Object> dataMap, String domainURI);

        /**
         * Convenience methods to properly construct lsids with the correct audit namespace
         */
        public String getDomainURI(String eventType);
        public String getPropertyURI(String eventType, String propertyName);

        public List<AuditLogEvent> getEvents(String eventType, String key);
        public List<AuditLogEvent> getEvents(String eventType, int key);
        public List<AuditLogEvent> getEvents(SimpleFilter filter);
        public List<AuditLogEvent> getEvents(SimpleFilter filter, Sort sort);
        public AuditLogEvent getEvent(int rowId);

        public AuditLogQueryView createQueryView(ViewContext context, @Nullable SimpleFilter filter);

        /**
         * Creates a query view specific to the audit view factory specified by the eventType parameter.
         * The audit view factory is able to customize the table info of the underlying query view.
         * @see org.labkey.api.audit.AuditLogService.AuditViewFactory
         */
        public AuditLogQueryView createQueryView(ViewContext context, @Nullable SimpleFilter filter, String eventType);

        public String getTableName();
        public TableInfo getTable(ViewContext context, String name);
        public UserSchema createSchema(User user, Container container);

        /**
         * An audit view factory is for creating customized views of specific audit event types.
         */
        public void addAuditViewFactory(AuditViewFactory factory);
        public AuditViewFactory getAuditViewFactory(String eventType);
        public List<AuditViewFactory> getAuditViewFactories();
    }

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
